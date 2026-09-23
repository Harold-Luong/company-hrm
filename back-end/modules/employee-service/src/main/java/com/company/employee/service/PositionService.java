package com.company.employee.service;

import com.company.employee.dto.PositionRequest;
import com.company.employee.dto.PositionResponse;
import com.company.employee.dto.PageResponse;
import com.company.employee.entity.Position;
import com.company.employee.exception.DuplicatePositionException;
import com.company.employee.exception.ResourceNotFoundException;
import com.company.employee.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionService {
    private final PositionRepository positionRepository;

    @Transactional
    public PositionResponse create(PositionRequest request) {
        if (positionRepository.existsByCode(request.code())) {
            throw new DuplicatePositionException();
        }
        Position position = new Position();
        position.setName(request.name());
        position.setCode(request.code());
        position.setDescription(request.description());
        positionRepository.save(position);
        return PositionResponse.from(position);
    }

    public PageResponse<PositionResponse> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("code").ascending());
        return PageResponse.from(positionRepository.findAll(pageable).map(PositionResponse::from));
    }

    public PositionResponse findById(UUID id) {
        return positionRepository.findById(id).map(PositionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Position", id));
    }

    @Transactional
    public PositionResponse update(UUID id, PositionRequest request) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position", id));
        if (positionRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new DuplicatePositionException();
        }
        position.setCode(request.code());
        position.setName(request.name());
        position.setDescription(request.description());
        return PositionResponse.from(position);
    }
}
