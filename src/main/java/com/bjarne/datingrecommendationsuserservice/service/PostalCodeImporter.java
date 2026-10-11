package com.bjarne.datingrecommendationsuserservice.service;

import com.bjarne.datingrecommendationsuserservice.repository.PostalCodeRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the postal_codes table on startup from files named postal-codes-&lt;COUNTRY&gt;.tsv
 * (format: postal_code, place_name, state, latitude, longitude; tab separated, '#' starts a comment).
 * A country is only imported while it has no rows yet. The data files are not part of the repository,
 * see scripts/prepare-postal-codes.sh.
 */
@Component
@Order(5)
public class PostalCodeImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostalCodeImporter.class);
    private static final Pattern FILE_NAME = Pattern.compile("postal-codes-([A-Za-z]{2})\\.tsv");
    private static final String INSERT = "insert into postal_codes (country_code, postal_code, place_name, state, latitude, longitude) values (?, ?, ?, ?, ?, ?)";
    private static final int BATCH_SIZE = 1000;

    private final PostalCodeRepository postalCodeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String location;

    public PostalCodeImporter(PostalCodeRepository postalCodeRepository,
                              JdbcTemplate jdbcTemplate,
                              TransactionTemplate transactionTemplate,
                              @Value("${geo.postal-codes.location:classpath*:geo/postal-codes-*.tsv}") String location) {
        this.postalCodeRepository = postalCodeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.location = location;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
        if (resources.length == 0) {
            LOGGER.warn("No postal code data found at '{}'. Addresses cannot be located until it is provided, "
                    + "run scripts/prepare-postal-codes.sh to create it.", location);
            return;
        }

        for (Resource resource : resources) {
            Matcher matcher = FILE_NAME.matcher(String.valueOf(resource.getFilename()));
            if (!matcher.matches()) {
                LOGGER.warn("Ignoring postal code file with unexpected name: {}", resource);
                continue;
            }

            String countryCode = matcher.group(1).toUpperCase(Locale.ROOT);
            if (postalCodeRepository.countByCountryCode(countryCode) > 0) {
                LOGGER.debug("Postal codes for {} already imported.", countryCode);
                continue;
            }

            List<Object[]> rows = read(resource, countryCode);
            transactionTemplate.executeWithoutResult(_ -> jdbcTemplate.batchUpdate(INSERT, rows, BATCH_SIZE, (statement, row) -> {
                for (int i = 0; i < row.length; i++) {
                    statement.setObject(i + 1, row[i]);
                }
            }));
            LOGGER.info("Imported {} postal codes for {} from {}.", rows.size(), countryCode, resource.getFilename());
        }
    }

    private static List<Object[]> read(Resource resource, String countryCode) throws IOException {
        List<Object[]> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] columns = line.split("\t", -1);
                rows.add(new Object[]{
                        countryCode,
                        columns[0],
                        columns[1],
                        columns[2].isBlank() ? null : columns[2],
                        Double.parseDouble(columns[3]),
                        Double.parseDouble(columns[4])
                });
            }
        }

        return rows;
    }
}
