package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;

import java.util.List;

/**
 * What the audit-log window asks for (TODO 6.21): a page of lines and their count. {@code AuditDAO} holds the permission and the query.
 */
public class AuditService {

    private final AuditDAO dao;

    public AuditService() {
        this(new AuditDAO());
    }

    AuditService(AuditDAO dao) {
        this.dao = dao;
    }

    public List<com.coffeeshop.coffeeshopmanagement.model.AuditEntry> find(AuditDAO.Filter filter, int limit, int offset) {
        return dao.find(filter, limit, offset);
    }

    public int count(AuditDAO.Filter filter) {
        return dao.count(filter);
    }
}
