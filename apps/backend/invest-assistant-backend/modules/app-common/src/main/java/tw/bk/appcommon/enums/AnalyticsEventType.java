package tw.bk.appcommon.enums;

/**
 * 產品分析事件類型。目前只收 page view；新增類型時於此擴充，
 * 避免在 SQL 或 service 內散落字串字面值。
 */
public enum AnalyticsEventType {
    PAGE_VIEW
}
