package com.example.shop.routing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutingService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public ConnectionInfo inWriteTransaction() {
        return query();
    }

    @Transactional(readOnly = true)
    public ConnectionInfo inReadOnlyTransaction() {
        return query();
    }

    private ConnectionInfo query() {
        Object[] row = (Object[]) em.createNativeQuery("SELECT @@server_id, CURRENT_USER(), @@read_only").getSingleResult();
        return new ConnectionInfo(((Number) row[0]).intValue(), (String) row[1], ((Number) row[2]).intValue() == 1);
    }
}
