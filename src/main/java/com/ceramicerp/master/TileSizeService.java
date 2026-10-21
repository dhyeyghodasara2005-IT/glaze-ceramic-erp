package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.TileSizeRequest;
import com.ceramicerp.master.dto.TileSizeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Business rules for tile sizes:
 * - codes are unique and stored upper-case ("600x1200" -> "600X1200")
 * - sq m per box is always calculated here, never typed by a user
 */
@Service
@RequiredArgsConstructor
public class TileSizeService {

    /** 1 square metre = 1000 mm x 1000 mm = 1,000,000 mm² */
    private static final BigDecimal MM2_PER_SQM = new BigDecimal("1000000");

    private final TileSizeRepository tileSizeRepository;

    @Transactional(readOnly = true)
    public List<TileSizeResponse> list() {
        return tileSizeRepository.findAllByOrderByLengthMmAscWidthMmAsc().stream()
                .map(TileSizeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TileSizeResponse get(Long id) {
        return TileSizeResponse.from(find(id));
    }

    @Transactional
    public TileSizeResponse create(TileSizeRequest req) {
        String code = Text.upperOrNull(req.code());
        if (tileSizeRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Size code already exists: " + code);
        }
        TileSize size = new TileSize();
        apply(size, req, code);
        return TileSizeResponse.from(tileSizeRepository.save(size));
    }

    @Transactional
    public TileSizeResponse update(Long id, TileSizeRequest req) {
        TileSize size = find(id);
        String code = Text.upperOrNull(req.code());
        if (tileSizeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Size code already exists: " + code);
        }
        apply(size, req, code);
        return TileSizeResponse.from(size);
    }

    /**
     * 600 mm x 1200 mm x 2 pieces = 1,440,000 mm² = 1.4400 sq m.
     * (long) stops the multiplication from overflowing for very large slabs.
     */
    static BigDecimal sqmPerBox(int lengthMm, int widthMm, int pcsPerBox) {
        long mm2 = (long) lengthMm * widthMm * pcsPerBox;
        return BigDecimal.valueOf(mm2).divide(MM2_PER_SQM, 4, RoundingMode.HALF_UP);
    }

    /** Also used later by DesignService (same package) to check a size exists. */
    TileSize find(Long id) {
        return tileSizeRepository.findById(id).orElseThrow(() -> new NotFoundException("Size", id));
    }

    private void apply(TileSize s, TileSizeRequest req, String code) {
        s.setCode(code);
        s.setName(req.name().trim());
        s.setLengthMm(req.lengthMm());
        s.setWidthMm(req.widthMm());
        s.setThicknessMm(req.thicknessMm());
        s.setPcsPerBox(req.pcsPerBox());
        s.setSqmPerBox(sqmPerBox(req.lengthMm(), req.widthMm(), req.pcsPerBox()));
        s.setBoxWeightKg(req.boxWeightKg());
        s.setBoxesPerPallet(req.boxesPerPallet());
        if (req.active() != null) {
            s.setActive(req.active());
        }
    }
}