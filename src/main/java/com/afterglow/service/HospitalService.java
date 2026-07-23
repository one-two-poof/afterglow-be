package com.afterglow.service;

import com.afterglow.repository.ContentItemRepository;
import com.afterglow.util.JsonHelper;
import com.afterglow.web.dto.HospitalResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HospitalService {

    private final ContentItemRepository contentItemRepository;
    private final JsonHelper jsonHelper;

    public HospitalService(ContentItemRepository contentItemRepository, JsonHelper jsonHelper) {
        this.contentItemRepository = contentItemRepository;
        this.jsonHelper = jsonHelper;
    }

    public List<HospitalResponse> listAll(boolean includeArchived) {
        var items = includeArchived
                ? contentItemRepository.findAllByOrderByNotionLastEditedAtDesc()
                : contentItemRepository.findByArchivedFalseOrderByNotionLastEditedAtDesc();
        return items.stream().map(item -> HospitalResponse.from(item, jsonHelper)).toList();
    }
}
