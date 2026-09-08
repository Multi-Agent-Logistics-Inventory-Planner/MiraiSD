package com.mirai.inventoryservice.catalog.application;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures every SQL statement Hibernate sends to the driver, for {@link CatalogQueriesEgressIT}
 * to inspect selected-column shape directly rather than inferring it from query counts alone.
 * Registered via {@code hibernate.session_factory.statement_inspector} (a class-name property —
 * Hibernate instantiates it by reflection, so state must be static, not instance-held).
 */
public class CapturingStatementInspector implements StatementInspector {

    private static final List<String> CAPTURED = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String inspect(String sql) {
        CAPTURED.add(sql);
        return sql;
    }

    static List<String> captured() {
        return new ArrayList<>(CAPTURED);
    }

    static void clear() {
        CAPTURED.clear();
    }
}
