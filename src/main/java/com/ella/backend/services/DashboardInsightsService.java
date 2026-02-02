package com.ella.backend.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.insights.InsightDeduplicator;
import com.ella.backend.services.insights.providers.InsightProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardInsightsService {

    private final List<InsightProvider> insightProviders;
    private final PersonRepository personRepository;

    public List<InsightDTO> getInsights(String personId, int year, int month) {
        Person person = resolvePerson(personId);

        List<InsightDTO> allInsights = new ArrayList<>();
        for (InsightProvider provider : insightProviders) {
            try {
                List<InsightDTO> providerInsights = provider.generate(person, year, month);
                if (providerInsights != null && !providerInsights.isEmpty()) {
                    allInsights.addAll(providerInsights);
                }
            } catch (Exception e) {
                // Segurança: um provider não deve derrubar o dashboard inteiro.
                log.warn(
                        "Insight provider {} failed for personId={}, year={}, month={}",
                        provider.getClass().getSimpleName(),
                        personId,
                        year,
                        month,
                        e
                );
            }
        }

        return InsightDeduplicator.deduplicate(allInsights).stream()
                .limit(5)
                .collect(Collectors.toList());
    }

    private Person resolvePerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        return personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));
    }
}
