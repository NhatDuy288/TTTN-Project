package com.tttn.qlnvl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialGroup;
import com.tttn.qlnvl.material.domain.MaterialUnit;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestConflictException;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestDraftCommand;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WarehouseRequestFlowIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tttn_integration");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private AppUserRepository users;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private MaterialGroupRepository groups;
    @Autowired private MaterialRepository materials;
    @Autowired private WarehouseRequestRepository requests;
    @Autowired private StatusHistoryRepository history;
    @Autowired private WarehouseRequestService requestService;
    @Autowired private RequestApprovalService requestApprovals;
    @Autowired private WarehouseTransactionRepository transactions;
    @Autowired private WarehouseTransactionService transactionService;
    @Autowired private TransactionApprovalService transactionApprovals;
    @Autowired private TransactionConfirmationService transactionConfirmations;
    @Autowired private WarehouseTransferRepository transfers;
    @Autowired private WarehouseTransferService transferService;
    @Autowired private TransferApprovalService transferApprovals;
    @Autowired private TransferConfirmationService transferConfirmations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @Test
    void requesterCanOpenUnfilteredOwnRequestListOnPostgreSql() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser(
                "it_list_owner", "unused", "List Owner", Role.REQUESTER));
        AppUser other = users.saveAndFlush(new AppUser(
                "it_list_other", "unused", "List Other", Role.REQUESTER));
        Warehouse destination = warehouses.saveAndFlush(new Warehouse(
                "KHO_IT_05", "IT Warehouse Five", "Integration test warehouse address", null, owner));
        Long operationId = jdbc.queryForObject(
                "select id from operation_type where code = 'IMP_OTHER'", Long.class);
        Long reasonId = jdbc.queryForObject(
                "select id from reason where code = 'IMPORT_RETURN'", Long.class);
        WarehouseRequest ownedRequest = requestService.createDraft(new WarehouseRequestDraftCommand(
                operationId, reasonId, null, destination.getId(), null, null, List.of()), owner.getId());
        WarehouseRequest otherRequest = requestService.createDraft(new WarehouseRequestDraftCommand(
                operationId, reasonId, null, destination.getId(), null, null, List.of()), other.getId());

        mvc.perform(get("/requests").with(user(AppUserPrincipal.from(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ownedRequest.getRequestCode())))
                .andExpect(content().string(not(containsString(otherRequest.getRequestCode()))));
    }

    @Test
    void importExportAndTransferPreserveStockAndAuditAcrossModules() throws Exception {
        AppUser requester = users.saveAndFlush(new AppUser(
                "it_import_requester", "unused", "Import Requester", Role.REQUESTER));
        AppUser requestApprover = users.saveAndFlush(new AppUser(
                "it_request_approver", "unused", "Request Approver", Role.REQUEST_APPROVER));
        AppUser inventoryStaff = users.saveAndFlush(new AppUser(
                "it_inventory_staff", "unused", "Inventory Staff", Role.INVENTORY_STAFF));
        AppUser inventoryApprover = users.saveAndFlush(new AppUser(
                "it_inventory_approver", "unused", "Inventory Approver", Role.INVENTORY_APPROVER));
        AppUser keeper = users.saveAndFlush(new AppUser(
                "it_keeper", "unused", "Warehouse Keeper", Role.WAREHOUSE_KEEPER));
        Warehouse destination = warehouses.saveAndFlush(new Warehouse(
                "KHO_IT_03", "IT Warehouse Three", "Integration test warehouse address", null, requester));
        MaterialGroup group = groups.findByCode("CARD-VL-KHAC").orElseThrow();
        Material material = materials.saveAndFlush(new Material(
                "IT_MAT_002", "Integration Receipt Material", group, MaterialUnit.CAI, null));
        Long operationId = jdbc.queryForObject(
                "select id from operation_type where code = 'IMP_OTHER'", Long.class);
        Long reasonId = jdbc.queryForObject(
                "select id from reason where code = 'IMPORT_RETURN'", Long.class);
        WarehouseRequest draft = requestService.createDraft(new WarehouseRequestDraftCommand(
                operationId, reasonId, null, destination.getId(), null, null,
                List.of(new WarehouseRequestDraftCommand.Detail(
                        material.getId(), MaterialCondition.OLD, 5L, null))), requester.getId());

        requestService.submit(draft.getId(), requester.getId());
        requestApprovals.approve(draft.getId(), requestApprover.getId(), "Approved request");
        Long transactionId = jdbc.queryForObject(
                "select id from warehouse_transaction where request_id = ?", Long.class, draft.getId());
        transactionService.submit(transactionId, inventoryStaff.getId(), null);
        transactionApprovals.approve(transactionId, inventoryApprover.getId(), "Approved execution");
        transactionConfirmations.confirm(transactionId, keeper.getId());

        assertThat(requests.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.COMPLETED);
        assertThat(transactions.findById(transactionId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransactionStatus.COMPLETED);
        Long onHand = jdbc.queryForObject("""
                select sum(on_hand_quantity) from inventory_lot
                where warehouse_id = ? and material_id = ? and condition = 'OLD'
                """, Long.class, destination.getId(), material.getId());
        assertThat(onHand).isEqualTo(5L);
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                AggregateType.REQUEST, draft.getId()))
                .extracting(StatusHistory::getAction)
                .containsExactly(WorkflowAction.SUBMIT, WorkflowAction.APPROVE,
                        WorkflowAction.START_PROCESSING, WorkflowAction.COMPLETE);
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                AggregateType.TRANSACTION, transactionId))
                .extracting(StatusHistory::getAction)
                .containsExactly(WorkflowAction.SUBMIT, WorkflowAction.APPROVE,
                        WorkflowAction.CONFIRM_PHYSICAL);
        mvc.perform(get("/workflow-history/TRANSACTION/" + transactionId)
                        .with(user(AppUserPrincipal.from(keeper))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRM_PHYSICAL")));
        mvc.perform(get("/workflow-history/TRANSACTION/" + transactionId)
                        .with(user(AppUserPrincipal.from(requester))))
                .andExpect(status().isForbidden());

        Long exportOperationId = jdbc.queryForObject(
                "select id from operation_type where code = 'EXP_OTHER'", Long.class);
        Long exportReasonId = jdbc.queryForObject(
                "select id from reason where code = 'EXPORT_USE'", Long.class);
        WarehouseRequest exportRequest = requestService.createDraft(new WarehouseRequestDraftCommand(
                exportOperationId, exportReasonId, destination.getId(), null, null, null,
                List.of(new WarehouseRequestDraftCommand.Detail(
                        material.getId(), MaterialCondition.OLD, 2L, null))), requester.getId());
        requestService.submit(exportRequest.getId(), requester.getId());
        requestApprovals.approve(exportRequest.getId(), requestApprover.getId(), null);
        Long exportTransactionId = jdbc.queryForObject(
                "select id from warehouse_transaction where request_id = ?",
                Long.class, exportRequest.getId());
        transactionService.submit(exportTransactionId, inventoryStaff.getId(), null);
        transactionApprovals.approve(exportTransactionId, inventoryApprover.getId(), null);
        transactionConfirmations.confirm(exportTransactionId, keeper.getId());
        assertThat(requests.findById(exportRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.COMPLETED);
        Long remainingAfterExport = jdbc.queryForObject("""
                select sum(on_hand_quantity) from inventory_lot
                where warehouse_id = ? and material_id = ? and condition = 'OLD'
                """, Long.class, destination.getId(), material.getId());
        assertThat(remainingAfterExport).isEqualTo(3L);
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                AggregateType.TRANSACTION, exportTransactionId))
                .extracting(StatusHistory::getAction)
                .containsExactly(WorkflowAction.SUBMIT, WorkflowAction.APPROVE,
                        WorkflowAction.CONFIRM_PHYSICAL);

        Warehouse transferDestination = warehouses.saveAndFlush(new Warehouse(
                "KHO_IT_04", "IT Warehouse Four", "Integration test warehouse address", null, requester));
        Long transferOperationId = jdbc.queryForObject(
                "select id from operation_type where code = 'WWT'", Long.class);
        Long transferReasonId = jdbc.queryForObject(
                "select id from reason where code = 'TRANSFER_INTERNAL'", Long.class);
        WarehouseRequest transferRequest = requestService.createDraft(new WarehouseRequestDraftCommand(
                transferOperationId, transferReasonId, destination.getId(),
                transferDestination.getId(), null, null,
                List.of(new WarehouseRequestDraftCommand.Detail(
                        material.getId(), MaterialCondition.OLD, 2L, null))), requester.getId());
        requestService.submit(transferRequest.getId(), requester.getId());
        requestApprovals.approve(transferRequest.getId(), requestApprover.getId(), null);
        Long transferId = jdbc.queryForObject(
                "select id from warehouse_transfer where request_id = ?",
                Long.class, transferRequest.getId());
        transferService.submit(transferId, inventoryStaff.getId());
        transferApprovals.approve(transferId, inventoryApprover.getId(), null);
        transferConfirmations.confirmSource(transferId, keeper.getId());
        assertThat(transfers.findById(transferId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.IN_TRANSIT);
        assertThat(jdbc.queryForObject("""
                select sum(on_hand_quantity) from inventory_lot
                where warehouse_id = ? and material_id = ? and condition = 'OLD'
                """, Long.class, destination.getId(), material.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                select count(*) from inventory_lot
                where warehouse_id = ? and material_id = ? and condition = 'OLD'
                """, Long.class, transferDestination.getId(), material.getId())).isZero();
        transferConfirmations.confirmDestination(transferId, keeper.getId());
        assertThat(transfers.findById(transferId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.COMPLETED);
        assertThat(requests.findById(transferRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.COMPLETED);
        assertThat(jdbc.queryForObject("""
                select sum(on_hand_quantity) from inventory_lot
                where warehouse_id = ? and material_id = ? and condition = 'OLD'
                """, Long.class, transferDestination.getId(), material.getId())).isEqualTo(2L);
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                AggregateType.TRANSFER, transferId))
                .extracting(StatusHistory::getAction)
                .containsExactly(WorkflowAction.SUBMIT, WorkflowAction.APPROVE,
                        WorkflowAction.CONFIRM_SOURCE, WorkflowAction.CONFIRM_DESTINATION);
    }

    @Test
    void submittedImportCancellationPersistsBothTransitions() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser(
                "it_submit_owner", "unused", "Submit Owner", Role.REQUESTER));
        Warehouse destination = warehouses.saveAndFlush(new Warehouse(
                "KHO_IT_02", "IT Warehouse Two", "Integration test warehouse address", null, owner));
        MaterialGroup group = groups.findByCode("CARD-VL-KHAC").orElseThrow();
        Material material = materials.saveAndFlush(new Material(
                "IT_MAT_001", "Integration Material", group, MaterialUnit.CAI, null));
        Long operationId = jdbc.queryForObject(
                "select id from operation_type where code = 'IMP_OTHER'", Long.class);
        Long reasonId = jdbc.queryForObject(
                "select id from reason where code = 'IMPORT_RETURN'", Long.class);
        WarehouseRequest draft = requestService.createDraft(new WarehouseRequestDraftCommand(
                operationId, reasonId, null, destination.getId(), null, null,
                List.of(new WarehouseRequestDraftCommand.Detail(
                        material.getId(), MaterialCondition.OLD, 2L, null))), owner.getId());

        requestService.submit(draft.getId(), owner.getId());
        assertThat(requests.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.SUBMITTED);
        requestService.cancel(draft.getId(), owner.getId());
        assertThat(requests.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.CANCELLED);

        List<StatusHistory> events = history
                .findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                        AggregateType.REQUEST, draft.getId());
        assertThat(events).extracting(StatusHistory::getAction)
                .containsExactly(WorkflowAction.SUBMIT, WorkflowAction.CANCEL);
        assertThat(events).extracting(StatusHistory::getToStatus)
                .containsExactly("SUBMITTED", "CANCELLED");
        mvc.perform(get("/workflow-history/REQUEST/" + draft.getId())
                        .with(user(AppUserPrincipal.from(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUBMIT")))
                .andExpect(content().string(containsString("CANCEL")));
    }

    @Test
    void draftCancellationPersistsOneEventAndEnforcesOwnership() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser("it_owner", "unused", "IT Owner", Role.REQUESTER));
        AppUser other = users.saveAndFlush(new AppUser("it_other", "unused", "IT Other", Role.REQUESTER));
        Warehouse destination = warehouses.saveAndFlush(
                new Warehouse("KHO_IT_01", "IT Warehouse", "Integration test warehouse address", null, owner));
        Long operationId = jdbc.queryForObject(
                "select id from operation_type where code = 'IMP_OTHER'", Long.class);
        Long reasonId = jdbc.queryForObject(
                "select id from reason where code = 'IMPORT_RETURN'", Long.class);

        WarehouseRequest draft = requestService.createDraft(new WarehouseRequestDraftCommand(
                operationId, reasonId, null, destination.getId(), null, null, List.of()), owner.getId());
        Long requestId = draft.getId();
        String path = "/workflow-history/REQUEST/" + requestId;

        mvc.perform(get(path).with(user(AppUserPrincipal.from(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có lịch sử")));
        mvc.perform(get(path).with(user(AppUserPrincipal.from(other))))
                .andExpect(status().isNotFound());

        requestService.cancel(requestId, owner.getId());
        assertThat(requests.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(WarehouseRequestStatus.CANCELLED);
        List<StatusHistory> events = history
                .findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(AggregateType.REQUEST, requestId);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getAction()).isEqualTo(WorkflowAction.CANCEL);
        assertThat(events.getFirst().getFromStatus()).isEqualTo("DRAFT");
        assertThat(events.getFirst().getToStatus()).isEqualTo("CANCELLED");
        assertThat(events.getFirst().getChangedBy().getId()).isEqualTo(owner.getId());

        mvc.perform(get(path).with(user(AppUserPrincipal.from(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CANCEL")))
                .andExpect(content().string(containsString("IT Owner")));
        assertThatThrownBy(() -> requestService.cancel(requestId, owner.getId()))
                .isInstanceOf(WarehouseRequestConflictException.class);
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
                AggregateType.REQUEST, requestId)).hasSize(1);
    }
}
