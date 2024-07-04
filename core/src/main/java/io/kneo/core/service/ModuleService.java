package io.kneo.core.service;

import io.kneo.core.dto.document.ModuleDTO;
import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.Module;
import io.kneo.core.model.user.AnonymousUser;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.ModuleRepository;
import io.kneo.core.repository.UserRepository;
import io.kneo.core.repository.exception.DocumentModificationAccessException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModuleService extends AbstractService<Module, ModuleDTO>  implements IRESTService<ModuleDTO> {
    private final ModuleRepository repository;

    protected ModuleService() {
        super(null, null);
        this.repository = null;
    }

    @Inject
    public ModuleService(UserRepository userRepository, UserService userService, ModuleRepository repository) {
        super(userRepository, userService);
        this.repository = repository;
    }

    public Uni<List<ModuleDTO>> getAll(final int limit, final int offset, LanguageCode languageCode) {
        Uni<List<Module>> listUni = repository.getAll(limit, offset);
        return listUni.onItem().transform(list -> list.stream()
                .map(doc ->
                        ModuleDTO.builder()
                                .id(doc.getId())
                                .author(userService.getUserName(doc.getAuthor()))
                                .regDate(doc.getRegDate())
                                .lastModifier(userService.getUserName(doc.getLastModifier()))
                                .lastModifiedDate(doc.getLastModifiedDate())
                                .identifier(doc.getIdentifier())
                                .localizedName(doc.getLocalizedName())
                                .localizedDescription(doc.getLocalizedDescription())
                                .build())
                .collect(Collectors.toList()));
    }

    @Override
    public Uni<Optional<ModuleDTO>> getByIdentifier(String identifier) {
        return null;
    }

    public Uni<Integer> getAllCount() {
        return repository.getAllCount();
    }

    public Uni<Integer> getAllCount(Long id) {
        return repository.getAllCount();
    }

    public Uni<List<ModuleDTO>> findAll(String[] defaultModules) {
        return repository.getModules(defaultModules)
                .onItem().transformToUni(modules ->
                        Multi.createFrom().iterable(modules)
                                .onItem().transformToUniAndMerge(moduleOpt ->
                                        moduleOpt.map(module ->
                                                mapToDTO(Uni.createFrom().item(Optional.of(module)))
                                        ).orElse(Uni.createFrom().nullItem())
                                )
                                .collect().asList()
                );
    }

    @Override
    public Uni<ModuleDTO> getDTO(String id, IUser user, LanguageCode language) {
        Uni<Optional<Module>> uni = repository.findById(UUID.fromString(id));
        return mapToDTO( uni);
    }

    @Override
    public Uni<UUID> add(ModuleDTO dto, IUser user) {
        return null;
    }

    @Override
    public Uni<Integer> update(String id, ModuleDTO dto, IUser user) {
        return null;
    }

    @Override
    public Uni<Integer> delete(String id, IUser user) throws DocumentModificationAccessException {
        return null;
    }

    private Uni<ModuleDTO> mapToDTO(Uni<Optional<Module>> uni) {
        return uni.onItem().transform(optional -> {
            Module doc = optional.orElseThrow();
            ModuleDTO dto = new ModuleDTO();
            dto.setId(doc.getId());
            dto.setAuthor(userService.getUserName(doc.getAuthor()));
            dto.setRegDate(doc.getRegDate());
            dto.setLastModifier(userService.getUserName(doc.getLastModifier()));
            dto.setLastModifiedDate(doc.getLastModifiedDate());
            dto.setIdentifier(doc.getIdentifier());
            dto.setOn(doc.isOn());
            dto.setLocalizedName(doc.getLocalizedName());
            dto.setLocalizedDescription(doc.getLocalizedDescription());
            return dto;
        });
    }

    public Uni<UUID> add(ModuleDTO dto) {
        Module doc = new Module.Builder()
                .setIdentifier(dto.getIdentifier())
                .setLocalizedName(dto.getLocalizedName())
                .setLocalizedDescription(dto.getLocalizedDescription())
                .build();
        return repository.insert(doc, AnonymousUser.ID);
    }

    public Module update(ModuleDTO dto) {
        Module doc = new Module.Builder()
                .setIdentifier(dto.getIdentifier())
                .build();
        return repository.update(doc);
    }

    public Uni<Void> delete (String id) {
        return repository.delete(UUID.fromString(id));
    }



}
