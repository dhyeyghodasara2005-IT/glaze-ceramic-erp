package com.ceramicerp.fg;

import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.DesignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The warehouse's settings per design: for now, which designs are best sellers. */
@Service
@RequiredArgsConstructor
public class FgDesignSettingService {

    private final FgDesignSettingRepository settingRepository;
    private final DesignRepository designRepository;

    @Transactional(readOnly = true)
    public List<Long> fastMovers() {
        return settingRepository.findFastMoverDesignIds();
    }

    /** Creates the design's settings row the first time it is needed. */
    @Transactional
    public void setFastMover(Long designId, boolean fastMover) {
        FgDesignSetting setting = settingRepository.findByDesignId(designId).orElseGet(() -> {
            FgDesignSetting s = new FgDesignSetting();
            s.setDesign(designRepository.findById(designId).orElseThrow(() -> new NotFoundException("Design", designId)));
            return s;
        });
        setting.setFastMover(fastMover);
        settingRepository.save(setting);
    }
}
