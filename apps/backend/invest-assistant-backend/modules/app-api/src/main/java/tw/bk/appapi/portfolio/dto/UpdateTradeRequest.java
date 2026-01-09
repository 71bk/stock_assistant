package tw.bk.appapi.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tw.bk.appportfolio.model.TradeSide;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTradeRequest {

        @NotNull(message = "Instrument ID is required")
        private String instrumentId;

        @NotNull(message = "Trade date is required")
        private LocalDate tradeDate;

        private LocalDate settlementDate;

        @NotNull(message = "Side is required")
        private TradeSide side;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than 0")
        private String quantity;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than 0")
        private String price;

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
        private String currency;

        private String fee;

        private String tax;

        private String accountId;
}
