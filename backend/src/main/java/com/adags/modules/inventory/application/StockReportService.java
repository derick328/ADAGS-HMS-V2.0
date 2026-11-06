package com.adags.modules.inventory.application;

import com.adags.modules.inventory.domain.CurrentStockRow;
import com.adags.modules.inventory.domain.ExpiringBatchRow;
import com.adags.modules.inventory.domain.LowStockRow;
import com.adags.modules.inventory.domain.StockValuationRow;
import com.adags.modules.inventory.domain.StockViewRepository;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;

import java.util.List;

// 12-pharmacist.md §6.1.
@Service
public class StockReportService {

    private static final String EXPIRY_WINDOW_KEY = "pharmacy.stock.expiry_watchlist_window_days";

    private final StockViewRepository stockViewRepository;
    private final ProtocolSettingReader protocolSettingReader;

    public StockReportService(StockViewRepository stockViewRepository, ProtocolSettingReader protocolSettingReader) {
        this.stockViewRepository = stockViewRepository;
        this.protocolSettingReader = protocolSettingReader;
    }

    public List<CurrentStockRow> currentStock() {
        return stockViewRepository.findCurrentStock();
    }

    public List<CurrentStockRow> currentStock(List<java.util.UUID> storeIds) {
        return storeIds.isEmpty() ? List.of() : stockViewRepository.findCurrentStock(storeIds);
    }

    public List<LowStockRow> lowStock() {
        return stockViewRepository.findLowStock();
    }

    public List<LowStockRow> lowStock(List<java.util.UUID> storeIds) {
        return storeIds.isEmpty() ? List.of() : stockViewRepository.findLowStock(storeIds);
    }

    /** §7.3's own default (90 days), admin-tunable without a redeploy. */
    public List<ExpiringBatchRow> expiringBatches() {
        int windowDays = (int) protocolSettingReader.getDouble(EXPIRY_WINDOW_KEY);
        return stockViewRepository.findExpiringWithin(windowDays);
    }

    public List<ExpiringBatchRow> expiringBatches(List<java.util.UUID> storeIds) {
        if (storeIds.isEmpty()) return List.of();
        int windowDays = (int) protocolSettingReader.getDouble(EXPIRY_WINDOW_KEY);
        return stockViewRepository.findExpiringWithin(windowDays, storeIds);
    }

    public List<StockValuationRow> stockValuation() {
        return stockViewRepository.findStockValuation();
    }

    public List<StockValuationRow> stockValuation(List<java.util.UUID> storeIds) {
        return storeIds.isEmpty() ? List.of() : stockViewRepository.findStockValuation(storeIds);
    }
}
