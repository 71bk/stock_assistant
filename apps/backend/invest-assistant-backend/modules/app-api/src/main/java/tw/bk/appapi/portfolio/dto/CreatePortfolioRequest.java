package tw.bk.appapi.portfolio.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePortfolioRequest {

        @Size(max = 100, message = "Portfolio name must be less than 100 characters")
        private String name;

        @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
        private String baseCurrency;
}
