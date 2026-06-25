package tw.bk.appapi.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminKeyStatusResponse {
    /** Whether the backend requires an admin key (one is configured server-side). */
    private boolean required;

    /** Whether the current request already carries a valid admin key. */
    private boolean active;
}
