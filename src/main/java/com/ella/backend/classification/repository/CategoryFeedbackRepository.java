package com.ella.backend.classification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.classification.entity.CategoryFeedback;

public interface CategoryFeedbackRepository extends JpaRepository<CategoryFeedback, UUID> {

		@Query("""
						select cf from CategoryFeedback cf
						where cf.userId = :userId
							and cf.transactionId in (
								select ft.id from FinancialTransaction ft
								where lower(ft.description) like lower(concat('%', :description, '%'))
							)
						order by cf.createdAt desc
						""")
		List<CategoryFeedback> findSimilarFeedback(
						@Param("userId") UUID userId,
						@Param("description") String description
		);
}
