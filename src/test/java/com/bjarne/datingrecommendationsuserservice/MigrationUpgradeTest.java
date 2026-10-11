package com.bjarne.datingrecommendationsuserservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migrates a database that contains data in the pre-Phase-1 schema (V2) to the latest version.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationUpgradeTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Test
    void existingDataIsMigratedToTheLatestSchema() throws Exception {
        flyway("2").migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    insert into users (reference_id, name, email, password, age, city, zip_code, country, status, photo)
                    values ('ref-1', 'Old User', 'old@example.com', 'hash', 30, 'Berlin', '10115', 'DE', 'ACTIVE',
                            'http://localhost:9000/profile-pictures/users/ref-1/my%20photo.jpg?X-Amz-Expires=604800&X-Amz-Signature=abc')
                    """);
            statement.execute("""
                    insert into users (reference_id, name, email, password, age, status)
                    values ('ref-2', 'Second', 'second@example.com', 'hash', 28, 'ACTIVE'),
                           ('ref-3', 'Third', 'third@example.com', 'hash', 35, 'ACTIVE')
                    """);
            // 1 and 2 like each other, 1 rejected 3, 3 blocked 1
            statement.execute("""
                    insert into contacts (user_id, contact_user_id, status)
                    select a.id, b.id, c.status
                    from (values ('ref-1', 'ref-2', 'ACCEPTED'), ('ref-2', 'ref-1', 'ACCEPTED'),
                                 ('ref-1', 'ref-3', 'REJECTED'), ('ref-3', 'ref-1', 'BLOCKED')) as c(a_ref, b_ref, status)
                             join users a on a.reference_id = c.a_ref
                             join users b on b.reference_id = c.b_ref
                    """);
        }

        flyway(null).migrate();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            ResultSet user = statement.executeQuery(
                    "select birth_date, photo_key, min_age, max_age, max_distance_km from users where reference_id = 'ref-1'");
            assertThat(user.next()).isTrue();
            assertThat(user.getDate("birth_date").toLocalDate()).isEqualTo(LocalDate.now().minusYears(30));
            assertThat(user.getString("photo_key")).isEqualTo("users/ref-1/my photo.jpg");
            assertThat(user.getInt("min_age")).isEqualTo(18);
            assertThat(user.getInt("max_age")).isEqualTo(99);
            assertThat(user.getInt("max_distance_km")).isEqualTo(50);

            ResultSet genders = statement.executeQuery("select count(*) from user_seeking_genders where user_id = (select id from users where reference_id = 'ref-1')");
            genders.next();
            assertThat(genders.getInt(1)).isEqualTo(3);

            assertThat(rows(statement, """
                    select f.reference_id || '>' || t.reference_id || ':' || r.type from reactions r
                    join users f on f.id = r.from_user_id join users t on t.id = r.to_user_id
                    """)).containsExactlyInAnyOrder("ref-1>ref-2:LIKE", "ref-2>ref-1:LIKE", "ref-1>ref-3:PASS");
            assertThat(rows(statement, """
                    select l.reference_id || '+' || h.reference_id || ':' || m.status from matches m
                    join users l on l.id = m.user_low_id join users h on h.id = m.user_high_id
                    """)).containsExactly("ref-1+ref-2:ACTIVE");
            assertThat(rows(statement, """
                    select a.reference_id || ' blocks ' || b.reference_id from blocks x
                    join users a on a.id = x.blocker_id join users b on b.id = x.blocked_id
                    """)).containsExactly("ref-3 blocks ref-1");
            assertThat(rows(statement, "select to_regclass('contacts')::text")).containsOnlyNulls();
        }
    }

    private static List<String> rows(Statement statement, String sql) throws Exception {
        List<String> rows = new ArrayList<>();
        try (ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                rows.add(result.getString(1));
            }
        }
        return rows;
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
