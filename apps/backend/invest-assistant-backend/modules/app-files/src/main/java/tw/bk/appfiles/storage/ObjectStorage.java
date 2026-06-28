package tw.bk.appfiles.storage;

import java.nio.file.Path;
import java.util.Map;

/**
 * 物件儲存後端（local / S3 / MinIO）的統一存取介面。
 *
 * <p>把實際的 byte I/O、client/credentials 建立與 presign 細節從 {@code FileService}
 * 抽出。{@code FileService} 只負責去重、metadata 與協調，依 provider 選用對應 adapter。
 * {@code bucket} 對 local adapter 無意義（傳 null 即可）。
 */
public interface ObjectStorage {

    /** 將暫存檔內容寫入指定 objectKey。 */
    void store(Path source, String bucket, String objectKey, String contentType);

    /** 讀取指定 objectKey 的內容。 */
    byte[] load(String bucket, String objectKey);

    /**
     * 產生直傳用的 presigned PUT。local provider 不支援，會丟出
     * {@link UnsupportedOperationException}（由 {@code FileService} 改走 multipart 回退）。
     */
    PresignedUpload presignPut(String bucket, String objectKey, String contentType, int expirySeconds);

    /**
     * 產生下載用的 presigned GET URL。local provider 不支援，會丟出
     * {@link UnsupportedOperationException}（由 {@code FileService} 改回傳本機 content path）。
     */
    String presignGet(String bucket, String objectKey, int expirySeconds);

    /** presigned 上傳的結果：URL、HTTP method 與需帶上的 header。 */
    record PresignedUpload(String url, String method, Map<String, String> headers) {
    }
}
