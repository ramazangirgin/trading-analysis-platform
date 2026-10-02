package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.time.LocalDate;

/** A trading day: close, volume and the moving averages upstream's market analyst quotes. */
public record PricePointDto(LocalDate date, double close, long volume, double ema10, double sma50, double sma200) {}
