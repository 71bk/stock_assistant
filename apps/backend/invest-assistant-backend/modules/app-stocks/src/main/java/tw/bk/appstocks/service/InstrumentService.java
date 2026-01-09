package tw.bk.appstocks.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.bk.apppersistence.entity.InstrumentEntity;
import tw.bk.apppersistence.repository.InstrumentRepository;

import java.util.List;
import java.util.Optional;

/**
 * 商品主檔服務
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    /**
     * 根據 symbol_key 查詢商品
     * 
     * @param symbolKey 商品唯一識別碼（如 US:XNAS:AAPL）
     * @return 商品實體
     */
    public Optional<InstrumentEntity> findBySymbolKey(String symbolKey) {
        return instrumentRepository.findBySymbolKey(symbolKey);
    }

    /**
     * 搜尋商品（自動補全用）
     * 支援 ticker 和 name 的模糊搜尋
     * 
     * @param query 搜尋關鍵字
     * @param limit 回傳數量限制（預設 10，最多 50）
     * @return 符合條件的商品列表
     */
    public List<InstrumentEntity> searchInstruments(String query, int limit) {
        // 限制回傳數量，避免效能問題
        int validLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, validLimit);

        return instrumentRepository.searchInstruments(query, pageable);
    }

    /**
     * 取得所有商品
     * 
     * @return 商品列表
     */
    public List<InstrumentEntity> findAll() {
        return instrumentRepository.findAll();
    }
}
