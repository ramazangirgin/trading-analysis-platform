package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetEntity;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetIdEmbeddable;

interface PresetJpaRepository
        extends JpaRepository<PresetEntity, PresetIdEmbeddable>, JpaSpecificationExecutor<PresetEntity> {

    /** One statement; the count it returns says whether a row went away. */
    @Modifying
    @Query("delete from PresetEntity p where p.id = :id")
    int deleteByIdCounting(@Param("id") PresetIdEmbeddable id);

    List<PresetEntity> findByOwnerIsNull();
}
