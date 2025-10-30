package com.bjarne.datingrecommendationsuserservice.dto;

import com.bjarne.datingrecommendationsuserservice.entity.Address;
import com.bjarne.datingrecommendationsuserservice.entity.Gender;
import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;

import java.util.List;

public record UserRequest(Long id,
                          String name,
                          String email,
                          String password,
                          List<String> interests,
                          List<String> hobbies,
                          Integer age,
                          Address address,
                          Gender gender,
                          UserStatus status
                          ) {}
