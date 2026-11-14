package com.b2bfintech.organisation.repository;

import com.b2bfintech.organisation.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    @Query("SELECT u FROM AppUser u WHERE u.phone = :phone")
    Optional<AppUser> findByPhoneNumber(@Param("phone") String phone);

    @Query("SELECT u FROM AppUser u WHERE u.phone = :identifier OR u.email = :identifier")
    Optional<AppUser> findByPhoneNumberOrEmail(@Param("identifier") String identifier);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByEmailAndUserIdNot(String email, String userId);

    boolean existsByUserType(AppUser.UserType userType);

    List<AppUser> findByUserOrgId(String userOrgId);

    List<AppUser> findByUserType(AppUser.UserType userType);
}
