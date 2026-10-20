package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.AnalysisDefaultsEntity;

interface AnalysisDefaultsJpaRepository extends JpaRepository<AnalysisDefaultsEntity, String> {}
