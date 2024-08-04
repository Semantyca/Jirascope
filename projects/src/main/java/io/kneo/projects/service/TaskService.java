package io.kneo.projects.service;

import io.kneo.core.dto.rls.RLSDTO;
import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.DataEntity;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.UserRepository;
import io.kneo.core.repository.exception.DocumentHasNotFoundException;
import io.kneo.core.service.AbstractService;
import io.kneo.core.service.UserService;
import io.kneo.core.service.exception.DataValidationException;
import io.kneo.core.util.DateUtil;
import io.kneo.core.util.NumberUtil;
import io.kneo.officeframe.dto.LabelDTO;
import io.kneo.officeframe.model.Label;
import io.kneo.officeframe.model.TaskType;
import io.kneo.officeframe.service.EmployeeService;
import io.kneo.officeframe.service.LabelService;
import io.kneo.officeframe.service.TaskTypeService;
import io.kneo.projects.dto.ProjectDTO;
import io.kneo.projects.dto.TaskDTO;
import io.kneo.projects.dto.TaskTemplateDTO;
import io.kneo.projects.dto.TaskTypeDTO;
import io.kneo.projects.model.Task;
import io.kneo.projects.repository.TaskRepository;
import io.kneo.projects.repository.table.ProjectNameResolver;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.kneo.projects.repository.table.ProjectNameResolver.TASK;

@ApplicationScoped
public class TaskService extends AbstractService<Task, TaskDTO> {
    private final TaskRepository repository;
    private final EmployeeService employeeService;
    private final LabelService labelService;
    private final ProjectService projectService;
    private final TaskTypeService taskTypeService;

    protected TaskService() {
        super(null, null);
        this.repository = null;
        this.employeeService = null;
        this.labelService = null;
        this.projectService = null;
        this.taskTypeService = null;
    }

    @Inject
    public TaskService(UserRepository userRepository,
                       UserService userService,
                       TaskRepository repository,
                       EmployeeService employeeService,
                       LabelService labelService,
                       ProjectService projectService,
                       TaskTypeService taskTypeService) {
        super(userRepository, userService);
        this.repository = repository;
        this.employeeService = employeeService;
        this.labelService = labelService;
        this.projectService = projectService;
        this.taskTypeService = taskTypeService;
    }

    public Uni<List<TaskDTO>> getAll(final int limit, final int offset, final IUser user) {
        assert repository != null;
        Uni<List<Task>> taskUni = repository.getAll(limit, offset, user.getId());
        return taskUni
                .onItem().transformToUni(taskList ->
                        Uni.combine().all().unis(
                                taskList.stream()
                                        .map(doc -> {
                                            assert employeeService != null;
                                            return employeeService.getById(doc.getAssignee())
                                                    .onFailure(DocumentHasNotFoundException.class).recoverWithNull()
                                                    .onItem().transform(assignee ->
                                                            TaskDTO.builder()
                                                                    .id(doc.getId())
                                                                    .author(userRepository.getUserName(doc.getAuthor()))
                                                                    .regDate(doc.getRegDate())
                                                                    .lastModifier(userRepository.getUserName(doc.getLastModifier()))
                                                                    .lastModifiedDate(doc.getLastModifiedDate())
                                                                    .targetDate(doc.getTargetDate())
                                                                    .priority(doc.getPriority())
                                                                    .status(doc.getStatus())
                                                                    .assignee(assignee)
                                                                    .build()
                                                    );
                                        }).collect(Collectors.toList())
                        ).with(list -> list.stream()
                                .map(item -> (TaskDTO) item)
                                .collect(Collectors.toList()))
                );
    }



    public Uni<Integer> getAllCount(final IUser user) {
        assert repository != null;
        return repository.getAllCount(user.getId());
    }


    private Uni<TaskDTO> map(Task doc) {
        assert employeeService != null;
        return employeeService.getById(doc.getAssignee())
                .onItem().transform(assignee -> TaskDTO.builder()
                        .id(doc.getId())
                        .author(userRepository.getUserName(doc.getAuthor()))
                        .regDate(doc.getRegDate())
                        .title(doc.getTitle())
                        .lastModifier(userRepository.getUserName(doc.getLastModifier()))
                        .lastModifiedDate(doc.getLastModifiedDate())
                        .regNumber(doc.getRegNumber())
                        .assignee(assignee)
                        .body(doc.getBody())
                        .startDate(LocalDate.from(doc.getStartDate()))
                        .targetDate(doc.getTargetDate())
                        .status(doc.getStatus())
                        .priority(doc.getPriority())
                        .build());
    }

    @Override
    public Uni<TaskDTO> getDTO(String uuid, IUser user, LanguageCode code) {
        UUID id = UUID.fromString(uuid);
        Uni<Optional<Task>> taskUni = repository.findById(id, user.getId());

        Uni<ProjectDTO> projectUni = taskUni.onItem().transformToUni(item ->
                projectService.get(item.get().getProject(), user)
        );

        Uni<Optional<TaskType>> taskTypeUni = taskUni.onItem().transformToUni(item ->
                taskTypeService.findById(item.get().getTaskType()));

        Uni<List<LabelDTO>> labelsUni = labelService.getLabels(id, ProjectNameResolver.create().getEntityNames(TASK).getLabelsName());

        Uni<List<RLSDTO>> rlsDtoListUni = getRLSDTO(repository, ProjectNameResolver.create().getEntityNames(TASK), taskUni, id);


        return Uni.combine().all().unis(taskUni, projectUni, taskTypeUni, labelsUni, rlsDtoListUni).with((taskOpt, project, taskType, labels, rls) -> {
                    Task task = taskOpt.orElseThrow();
                    return TaskDTO.builder()
                            .id(task.getId())
                            .author(userRepository.getUserName(task.getAuthor()))
                            .regDate(task.getRegDate())
                            .title(task.getTitle())
                            .lastModifier(userRepository.getUserName(task.getLastModifier()))
                            .lastModifiedDate(task.getLastModifiedDate())
                            .regNumber(task.getRegNumber())
                            .body(task.getBody())
                            //   .assignee(getAssigneeDTO(userService.findById(task.getAssignee()), task))
                            .taskType(TaskTypeDTO.builder()
                                    .identifier(taskType.orElseThrow().getIdentifier())
                                    .localizedName(taskType.orElseThrow().getLocalizedName(LanguageCode.ENG))
                                    .build())
                            .project(project)
                            .startDate(LocalDate.from(task.getStartDate()))
                            .targetDate(task.getTargetDate())
                            .status(task.getStatus())
                            .priority(task.getPriority())
                            .labels(labels)
                            .rls(rls).build();
                }
        );

    }

    public Uni<TaskTemplateDTO> getTemplate(IUser user) {
        TaskTemplateDTO template = TaskTemplateDTO.builder()
                .targetDate(LocalDate.now().plusMonths(1))
                .build();
        return Uni.createFrom().item(template);
    }

    public Uni<Integer> delete(String id, IUser user) {
        assert repository != null;
        return repository.delete(UUID.fromString(id), user.getId());
    }

    private Task buildEntity(TaskDTO dto, Optional<IUser> assignee, List<Label> labels, Optional<TaskType> taskType, ProjectDTO project, Optional<Task> taskOpt) {
        Task doc = new Task.Builder()
                .setRegNumber(String.valueOf(NumberUtil.getRandomNumber(100000, 999999)))
                .setBody(dto.getBody())
                .setAssignee(assignee.orElseThrow().getId())
                .setPriority(dto.getPriority())
                .setCancellationComment(dto.getCancellationComment())
                .setTitle(dto.getTitle())
                .setLabels(labels.stream()
                        .map(DataEntity::getId)
                        .collect(Collectors.toList()))
                .setTaskType(taskType.orElseThrow(() -> new DataValidationException("Task type is not correct")).getId())
                .setProject(project.getId())
                .setStartDate(DateUtil.getStartOfDayOrNow(dto.getStartDate()))
                .build();
        taskOpt.ifPresent(task -> doc.setParent(task.getParent()));
        if (dto.getTargetDate() != null) {
            doc.setTargetDate(dto.getTargetDate());
        }
        return doc;
    }

}
