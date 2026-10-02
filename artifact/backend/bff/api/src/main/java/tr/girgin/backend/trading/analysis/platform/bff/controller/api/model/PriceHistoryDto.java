package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.LocalDate;
import java.util.List;

/** About a year of daily prices up to an analysis' trade date, oldest first. */
public record PriceHistoryDto(String ticker, LocalDate tradeDate, List<PricePointDto> points) {}
