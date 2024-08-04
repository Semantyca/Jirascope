package io.kneo.officeframe.service;

import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.UserRepository;
import io.kneo.core.repository.exception.DocumentModificationAccessException;
import io.kneo.core.service.AbstractService;
import io.kneo.core.service.IRESTService;
import io.kneo.core.service.UserService;
import io.kneo.officeframe.dto.PositionDTO;
import io.kneo.officeframe.model.Position;
import io.kneo.officeframe.repository.PositionRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PositionService extends AbstractService<Position, PositionDTO> implements IRESTService<PositionDTO> {
    private final PositionRepository repository;


    public PositionService(UserRepository userRepository, UserService userService, PositionRepository repository) {
        super(userRepository, userService);
        this.repository = repository;
    }

    public Uni<List<PositionDTO>> getAll(final int limit, final int offset, LanguageCode languageCode) {
        Uni<List<Position>> listUni = repository.getAll(limit, offset);
        return listUni
                .onItem().transform(taskList -> taskList.stream()
                        .map(doc ->
                                PositionDTO.builder()
                                        .id(doc.getId())
                                        .author(userRepository.getUserName(doc.getAuthor()))
                                        .regDate(doc.getRegDate())
                                        .lastModifier(userRepository.getUserName(doc.getLastModifier()))
                                        .lastModifiedDate(doc.getLastModifiedDate())
                                        .identifier(doc.getIdentifier())
                                        .localizedName(doc.getLocalizedName())
                                        .build())
                        .collect(Collectors.toList()));
    }

    @Override
    public Uni<Integer> getAllCount() {
        return repository.getAllCount();
    }

    @Override
    public Uni<PositionDTO> getByIdentifier(String identifier) {
        return null;
    }

    public Uni<PositionDTO> getDTO(String uuid, IUser user, LanguageCode language) {
        return getDTO(UUID.fromString(uuid));
    }

    @Override
    public Uni<PositionDTO> add(PositionDTO dto, IUser user) {
        return null;
    }

    @Override
    public Uni<PositionDTO> update(String id, PositionDTO dto, IUser user) {
        return null;
    }

    @Override
    public Uni<Integer> delete(String id, IUser user) throws DocumentModificationAccessException {
        return null;
    }

    public Uni<PositionDTO> getDTO(UUID uuid) {
        Uni<Position> uni = repository.findById(uuid);
        return uni.onItem().transform(doc -> {
                return PositionDTO.builder()
                        .author(userRepository.getUserName(doc.getAuthor()))
                        .regDate(doc.getRegDate())
                        .lastModifier(userRepository.getUserName(doc.getLastModifier()))
                        .lastModifiedDate(doc.getLastModifiedDate())
                        .identifier(doc.getIdentifier())
                        .localizedName(doc.getLocalizedName())
                        .build();

        });
    }

    public Uni<Position> get(UUID uuid) {
        return repository.findById(uuid);
    }

}
