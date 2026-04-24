package com.capdi.backend.domain.company.repository;

import com.capdi.backend.domain.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Company 데이터 접근 Repository
 * 기본 CRUD 기능은 JpaRepository에서 제공된다.
 */
public interface CompanyRepository extends JpaRepository<Company, Long> {
}