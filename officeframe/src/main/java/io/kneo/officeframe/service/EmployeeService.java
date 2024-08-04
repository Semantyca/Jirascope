package io.kneo.officeframe.service;

import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.UserRepository;
import io.kneo.core.service.AbstractService;
import io.kneo.core.service.IRESTService;
import io.kneo.core.service.UserService;
import io.kneo.officeframe.dto.DepartmentDTO;
import io.kneo.officeframe.dto.EmployeeDTO;
import io.kneo.officeframe.dto.OrganizationDTO;
import io.kneo.officeframe.dto.PositionDTO;
import io.kneo.officeframe.model.Employee;
import io.kneo.officeframe.repository.DepartmentRepository;
import io.kneo.officeframe.repository.EmployeeRepository;
import io.kneo.officeframe.repository.OrganizationRepository;
import io.kneo.officeframe.repository.PositionRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmployeeService extends AbstractService<Employee, EmployeeDTO> implements IRESTService<EmployeeDTO> {
    private static final String CURRENT_KEYWORD = "current";
    private final EmployeeRepository repository;
    private final OrganizationRepository orgRepository;
    private final DepartmentRepository depRepository;
    private final PositionRepository positionRepository;
    private final OrganizationService organizationService;
    private final DepartmentService departmentService;
    private final PositionService positionService;

    protected EmployeeService() {
        super(null, null);
        this.repository = null;
        this.orgRepository = null;
        this.depRepository = null;
        this.positionRepository = null;
        this.positionService = null;
        this.organizationService = null;
        this.departmentService = null;
    }

    @Inject
    public EmployeeService(UserRepository userRepository,
                           UserService userService,
                           EmployeeRepository repository,
                           OrganizationRepository orgRepository,
                           DepartmentRepository depRepository,
                           PositionRepository positionRepository,
                           PositionService positionService,
                           OrganizationService organizationService,
                           DepartmentService departmentService) {
        super(userRepository, userService);
        this.repository = repository;
        this.orgRepository = orgRepository;
        this.depRepository = depRepository;
        this.positionRepository = positionRepository;
        this.positionService = positionService;
        this.organizationService = organizationService;
        this.departmentService = departmentService;
    }

    public Uni<List<EmployeeDTO>> getAll(final int limit, final int offset, LanguageCode languageCode) {
        assert repository != null;
        Uni<List<Employee>> listUni = repository.getAll(limit, offset);
        return listUni
                .onItem().transformToUni(employees ->
                        Uni.combine().all().unis(
                                employees.stream()
                                        .map(this::map)
                                        .collect(Collectors.toList())
                        ).with(list -> list.stream()
                                .map(result -> (EmployeeDTO) result)
                                .collect(Collectors.toList()))
                );
    }

    public Uni<List<EmployeeDTO>> search(String keyword, LanguageCode languageCode) {
        assert repository != null;
        return repository.search(keyword)
                .onItem().transformToUni(employeeList ->
                        Uni.combine().all().unis(
                                employeeList.stream()
                                        .map(this::map)
                                        .collect(Collectors.toList())
                        ).with(list -> list.stream()
                                .map(result -> (EmployeeDTO) result)
                                .collect(Collectors.toList()))
                );
    }



    @Override
    public Uni<Integer> getAllCount() {
        assert repository != null;
        return repository.getAllCount();
    }

    @Override
    public Uni<EmployeeDTO> getByIdentifier(String identifier) {
        assert repository != null;
        return map(repository.getByIdentifier(identifier));
    }

    public Uni<EmployeeDTO> getById(long id) {
        assert repository != null;
        return map(repository.getByUserId(id));
    }

    @Override
    public Uni<EmployeeDTO> getDTO(String id, IUser user, LanguageCode language) {
        Uni<Employee> uni;
        if ("current".equals(id)) {
            assert repository != null;
            uni = repository.getByUserId(user.getId());
        } else {
            assert repository != null;
            uni = repository.getById(UUID.fromString(id));
        }
        return map(uni);
    }

    public Uni<EmployeeDTO> upsert(String id, EmployeeDTO dto, IUser user) {
        Employee doc = new Employee();
        doc.setIdentifier(dto.getIdentifier());
        doc.setPhone(dto.getPhone());
        doc.setDepartment(dto.getDep().getId());
        doc.setLocalizedName(dto.getLocalizedName());
        doc.setOrganization(dto.getOrg().getId());
        doc.setBirthDate(dto.getBirthDate());
        doc.setPosition(dto.getPosition().getId());
        doc.setRoles(null);
        doc.setRank(dto.getRank());
        doc.setBirthDate(dto.getBirthDate());
        if (id == null) {
            assert repository != null;
            return map(repository.insert(doc, user));
        } else {
            UUID uuid = UUID.fromString(id);
            assert repository != null;
            return map(repository.update(uuid, doc, user));
        }
    }

    @Override
    public Uni<EmployeeDTO> add(EmployeeDTO dto, IUser user) {
        return null;
    }

    @Override
    public Uni<EmployeeDTO> update(String id, EmployeeDTO dto, IUser user) {
        return null;
    }

    public Uni<Integer> delete(String id, IUser user) {
        assert repository != null;
        return repository.delete(UUID.fromString(id));
    }

    private Uni<EmployeeDTO> map(Employee doc) {
        return mapCommon(doc);
    }

    private Uni<EmployeeDTO> map(Uni<Employee> employeeUni) {
        return employeeUni.onItem().transformToUni(this::mapCommon);
    }


    private Uni<EmployeeDTO> mapCommon(Employee doc) {
        Uni<PositionDTO> positionDTOUni;
        if (doc.getPosition() == null) {
            positionDTOUni = Uni.createFrom().nullItem();
        } else {
            assert positionService != null;
            positionDTOUni = positionService.getDTO(doc.getPosition()).onFailure().recoverWithNull();
        }

        return positionDTOUni.onItem().transformToUni(position -> {
            EmployeeDTO dto = EmployeeDTO.builder()
                    .id(doc.getId())
                    .userId(doc.getUser())
                    .author(userRepository.getUserName(doc.getAuthor()))
                    .regDate(doc.getRegDate())
                    .lastModifier(userRepository.getUserName(doc.getLastModifier()))
                    .lastModifiedDate(doc.getLastModifiedDate())
                    .phone(doc.getPhone())
                    .rank(doc.getRank())
                    .position(position)
                    .localizedName(doc.getLocalizedName())
                    .identifier(doc.getIdentifier())
                    .build();

            List<Uni<?>> unis = new ArrayList<>();

            if (doc.getDepartment() != null) {
                assert departmentService != null;
                unis.add(departmentService.get(doc.getDepartment())
                        .onItem().transformToUni(department -> {
                            dto.setDep(DepartmentDTO.builder()
                                    .id(department.getId())
                                    .identifier(department.getIdentifier())
                                    .localizedName(department.getLocalizedName())
                                    .build());
                            return Uni.createFrom().item(dto);
                        }));
            }

            if (doc.getOrganization() != null) {
                unis.add(organizationService.get(doc.getOrganization())
                        .onItem().transformToUni(organization -> {
                            dto.setOrg(OrganizationDTO.builder()
                                    .id(organization.getId())
                                    .identifier(organization.getIdentifier())
                                    .localizedName(organization.getLocalizedName())
                                    .build());
                            return Uni.createFrom().item(dto);
                        }));
            }

            if (unis.isEmpty()) {
                return Uni.createFrom().item(dto);
            } else {
                return Uni.combine().all().unis(unis).with(ignored -> dto);
            }
        });
    }



}
