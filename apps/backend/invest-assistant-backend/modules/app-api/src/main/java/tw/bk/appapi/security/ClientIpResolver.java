package tw.bk.appapi.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析請求來源 IP，作為 rate limit key 與登入稽核用。
 *
 * <p>只有在「信任的反向代理」情境下才採信 {@code X-Forwarded-For}/{@code X-Real-IP}，
 * 否則一律用 {@code getRemoteAddr()}，避免來源 IP 被偽造。把這段基礎設施判定從
 * {@code AuthController} 抽出。信任設定由呼叫端（持有 {@code @Value} 設定）傳入。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * @param trustedProxyEnabled 是否啟用信任代理（未啟用則永遠回 remote addr）
     * @param trustedProxyIpList  逗號分隔的信任代理 IP 清單（loopback 一律信任）
     */
    public static String resolve(HttpServletRequest request, boolean trustedProxyEnabled, String trustedProxyIpList) {
        if (isTrustedProxyRequest(request, trustedProxyEnabled, trustedProxyIpList)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isTrustedProxyRequest(
            HttpServletRequest request, boolean trustedProxyEnabled, String trustedProxyIpList) {
        if (!trustedProxyEnabled) {
            return false;
        }

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        if (isLoopback(remoteAddr)) {
            return true;
        }

        if (trustedProxyIpList == null || trustedProxyIpList.isBlank()) {
            return false;
        }
        for (String candidate : trustedProxyIpList.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty() && trimmed.equals(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
