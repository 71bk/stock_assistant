package tw.bk.appauth.model;

public record UserView(
        Long id,
        String email,
        String displayName,
        String pictureUrl) {
}
