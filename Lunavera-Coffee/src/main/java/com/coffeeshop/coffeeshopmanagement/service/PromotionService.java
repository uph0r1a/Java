package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.PromotionDAO;

import java.util.List;

/**
 * What the promotions window asks for (TODO 6.21): the list, saving, switching on or off, deleting. {@code PromotionDAO} holds the permission, the checks and the audit lines.
 */
public class PromotionService {

    private final PromotionDAO dao;

    public PromotionService() {
        this(new PromotionDAO());
    }

    PromotionService(PromotionDAO dao) {
        this.dao = dao;
    }

    public List<AutoPromotionRules.Promotion> findAll() {
        return dao.findAll();
    }

    public AutoPromotionRules.Promotion save(AutoPromotionRules.Promotion promotion) {
        return dao.save(promotion);
    }

    public void setActive(int id, boolean active) {
        dao.setActive(id, active);
    }

    public void delete(int id) {
        dao.delete(id);
    }
}
