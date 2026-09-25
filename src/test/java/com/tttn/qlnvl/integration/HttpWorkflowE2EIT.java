package com.tttn.qlnvl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.domain.Role;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.shared.audit.AggregateType;
import com.tttn.qlnvl.shared.audit.StatusHistory;
import com.tttn.qlnvl.shared.audit.StatusHistoryRepository;
import com.tttn.qlnvl.shared.audit.WorkflowAction;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HttpWorkflowE2EIT extends PostgreSqlIntegrationTestBase {
    private static final String PASSWORD = "http-e2e-password";

    @Autowired AppUserRepository users;
    @Autowired WarehouseRepository warehouses;
    @Autowired MaterialGroupRepository groups;
    @Autowired MaterialRepository materials;
    @Autowired WarehouseRequestRepository requests;
    @Autowired WarehouseTransactionRepository transactions;
    @Autowired WarehouseTransferRepository transfers;
    @Autowired StatusHistoryRepository history;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    String key;
    IntegrationFixtureFactory fixtures;
    Sessions sessions;
    Warehouse source;
    Warehouse destination;
    Material material;

    @BeforeEach
    void setUpIsolatedFixture() throws Exception {
        fixtures = new IntegrationFixtureFactory(users, warehouses, groups, materials);
        key = fixtures.nextKey("http");
        AppUser requester = user("requester", Role.REQUESTER);
        AppUser other = user("other", Role.REQUESTER);
        AppUser requestApprover = user("request-approver", Role.REQUEST_APPROVER);
        AppUser staff = user("staff", Role.INVENTORY_STAFF);
        AppUser inventoryApprover = user("inventory-approver", Role.INVENTORY_APPROVER);
        AppUser keeper = user("keeper", Role.WAREHOUSE_KEEPER);
        sessions = new Sessions(login(requester), login(other), login(requestApprover),
                login(staff), login(inventoryApprover), login(keeper));
        source = fixtures.warehouse(key, "SOURCE", keeper);
        destination = fixtures.warehouse(key, "DESTINATION", keeper);
        material = fixtures.material(key, "HTTP E2E Material");
    }

    @Test
    void fiveRoleHappyPathsUseSessionsCsrfAndPostgreSqlState() throws Exception {
        mvc.perform(get("/requests")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/request-approvals").session(sessions.requester()))
                .andExpect(status().isForbidden());

        long importId = createRequest("IMP_OTHER", "IMPORT_RETURN", null, destination.getId(), 5);
        mvc.perform(get("/requests/" + importId).session(sessions.other()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/requests/" + importId + "/submit")
                        .session(sessions.requester()).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(requestStatus(importId)).isEqualTo(WarehouseRequestStatus.DRAFT);

        mvc.perform(post("/requests/" + importId).session(sessions.requester()).with(csrf())
                        .param("operationTypeId", codeId("operation_type", "IMP_OTHER"))
                        .param("reasonId", codeId("reason", "IMPORT_RETURN"))
                        .param("destinationWarehouseId", destination.getId().toString())
                        .param("note", "edited through HTTP")
                        .param("details[0].materialId", material.getId().toString())
                        .param("details[0].condition", "OLD")
                        .param("details[0].quantity", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/" + importId));
        completeImport(importId);
        long importTransactionId = transactionId(importId);
        assertThat(stock(destination)).isEqualTo(5);
        assertActions(AggregateType.REQUEST, importId, WorkflowAction.SUBMIT,
                WorkflowAction.APPROVE, WorkflowAction.START_PROCESSING, WorkflowAction.COMPLETE);
        assertActions(AggregateType.TRANSACTION, importTransactionId, WorkflowAction.SUBMIT,
                WorkflowAction.APPROVE, WorkflowAction.CONFIRM_PHYSICAL);

        long exportId = createRequest("EXP_OTHER", "EXPORT_USE", destination.getId(), null, 2);
        submitRequest(exportId);
        assertThat(jdbc.queryForObject("select status from stock_reservation where request_detail_id = "
                + "(select id from warehouse_request_detail where request_id = ?)",
                String.class, exportId)).isEqualTo("ACTIVE_SOFT");
        approveRequest(exportId);
        assertThat(jdbc.queryForObject("select count(*) from issue_lot_allocation a "
                + "join warehouse_transaction_detail d on d.id=a.transaction_detail_id "
                + "join warehouse_transaction t on t.id=d.transaction_id where t.request_id=?",
                Long.class, exportId)).isEqualTo(1);
        long exportTransactionId = transactionId(exportId);
        submitTransaction(exportTransactionId);
        approveTransaction(exportTransactionId);
        confirmTransaction(exportTransactionId);
        assertThat(stock(destination)).isEqualTo(3);

        long transferRequestId = createRequest("WWT", "TRANSFER_INTERNAL",
                destination.getId(), source.getId(), 2);
        submitRequest(transferRequestId);
        approveRequest(transferRequestId);
        long transferId = transferId(transferRequestId);
        submitTransfer(transferId);
        approveTransfer(transferId);
        postAs("/transfer-confirmations/source/" + transferId + "/confirm", sessions.keeper());
        assertThat(transfers.findById(transferId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.IN_TRANSIT);
        assertThat(stock(destination)).isEqualTo(1);
        assertThat(stock(source)).isZero();
        postAs("/transfer-confirmations/destination/" + transferId + "/confirm", sessions.keeper());
        assertThat(transfers.findById(transferId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.COMPLETED);
        assertThat(requestStatus(transferRequestId)).isEqualTo(WarehouseRequestStatus.COMPLETED);
        assertThat(stock(source)).isEqualTo(2);
        assertActions(AggregateType.TRANSFER, transferId, WorkflowAction.SUBMIT,
                WorkflowAction.APPROVE, WorkflowAction.CONFIRM_SOURCE,
                WorkflowAction.CONFIRM_DESTINATION);
        mvc.perform(get("/workflow-history/TRANSFER/" + transferId).session(sessions.keeper()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRM_DESTINATION")));
    }

    @Test
    void validationAndRequestTransactionTransferRejectionsUseHttp() throws Exception {
        long before = requests.count();
        mvc.perform(post("/requests").session(sessions.requester()).with(csrf())
                        .param("reasonId", codeId("reason", "IMPORT_RETURN")))
                .andExpect(status().isOk()).andExpect(view().name("requests/form"));
        assertThat(requests.count()).isEqualTo(before);

        long cancelledRequestId = createRequest("IMP_OTHER", "IMPORT_RETURN",
                null, destination.getId(), 1);
        submitRequest(cancelledRequestId);
        postAs("/requests/" + cancelledRequestId + "/cancel", sessions.requester());
        assertThat(requestStatus(cancelledRequestId)).isEqualTo(WarehouseRequestStatus.CANCELLED);
        assertActions(AggregateType.REQUEST, cancelledRequestId,
                WorkflowAction.SUBMIT, WorkflowAction.CANCEL);

        long requestId = createRequest("IMP_OTHER", "IMPORT_RETURN", null, destination.getId(), 1);
        submitRequest(requestId);
        decision("/request-approvals/" + requestId + "/reject", sessions.requestApprover());
        assertThat(requestStatus(requestId)).isEqualTo(WarehouseRequestStatus.REJECTED);

        long transactionRequestId = createRequest("IMP_OTHER", "IMPORT_RETURN",
                null, destination.getId(), 1);
        submitRequest(transactionRequestId);
        approveRequest(transactionRequestId);
        long transactionId = transactionId(transactionRequestId);
        submitTransaction(transactionId);
        decision("/transaction-approvals/" + transactionId + "/reject",
                sessions.inventoryApprover());
        assertThat(transactions.findById(transactionId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransactionStatus.REJECTED);
        assertThat(requestStatus(transactionRequestId)).isEqualTo(WarehouseRequestStatus.CANCELLED);

        long stockRequestId = createRequest("IMP_OTHER", "IMPORT_RETURN",
                null, destination.getId(), 2);
        completeImport(stockRequestId);
        long transferRequestId = createRequest("WWT", "TRANSFER_INTERNAL",
                destination.getId(), source.getId(), 1);
        submitRequest(transferRequestId);
        approveRequest(transferRequestId);
        long transferId = transferId(transferRequestId);
        submitTransfer(transferId);
        decision("/transfer-approvals/" + transferId + "/reject", sessions.inventoryApprover());
        assertThat(transfers.findById(transferId).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.REJECTED);
        assertThat(requestStatus(transferRequestId)).isEqualTo(WarehouseRequestStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_lot "
                + "where warehouse_id=? and material_id=?", Long.class,
                destination.getId(), material.getId())).isZero();
    }

    AppUser user(String label, Role role) {
        return fixtures.user(key, label, role, encoder.encode(PASSWORD));
    }

    MockHttpSession login(AppUser user) throws Exception {
        MvcResult result = mvc.perform(formLogin().user(user.getUsername()).password(PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    long createRequest(String operation, String reason, Long sourceId, Long destinationId,
            long quantity) throws Exception {
        var builder = post("/requests").session(sessions.requester()).with(csrf())
                .param("operationTypeId", codeId("operation_type", operation))
                .param("reasonId", codeId("reason", reason))
                .param("details[0].materialId", material.getId().toString())
                .param("details[0].condition", "OLD")
                .param("details[0].quantity", Long.toString(quantity));
        if (sourceId != null) builder.param("sourceWarehouseId", sourceId.toString());
        if (destinationId != null) builder.param("destinationWarehouseId", destinationId.toString());
        MvcResult result = mvc.perform(builder).andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/requests/");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    void completeImport(long requestId) throws Exception {
        submitRequest(requestId);
        approveRequest(requestId);
        long transactionId = transactionId(requestId);
        submitTransaction(transactionId);
        approveTransaction(transactionId);
        confirmTransaction(transactionId);
    }

    void submitRequest(long id) throws Exception {
        postAs("/requests/" + id + "/submit", sessions.requester());
        assertThat(requestStatus(id)).isEqualTo(WarehouseRequestStatus.SUBMITTED);
    }

    void approveRequest(long id) throws Exception {
        decision("/request-approvals/" + id + "/approve", sessions.requestApprover());
        assertThat(requestStatus(id)).isEqualTo(WarehouseRequestStatus.PROCESSING);
    }

    void submitTransaction(long id) throws Exception {
        mvc.perform(post("/warehouse-transactions/" + id + "/submit")
                        .session(sessions.staff()).with(csrf())
                        .param("executionNote", "HTTP execution"))
                .andExpect(status().is3xxRedirection());
        assertThat(transactions.findById(id).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransactionStatus.SUBMITTED);
    }

    void approveTransaction(long id) throws Exception {
        decision("/transaction-approvals/" + id + "/approve", sessions.inventoryApprover());
        assertThat(transactions.findById(id).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransactionStatus.READY_FOR_CONFIRMATION);
    }

    void confirmTransaction(long id) throws Exception {
        postAs("/transaction-confirmations/" + id + "/confirm", sessions.keeper());
        assertThat(transactions.findById(id).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransactionStatus.COMPLETED);
    }

    void submitTransfer(long id) throws Exception {
        postAs("/warehouse-transfers/" + id + "/submit", sessions.staff());
        assertThat(transfers.findById(id).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.SUBMITTED);
    }

    void approveTransfer(long id) throws Exception {
        decision("/transfer-approvals/" + id + "/approve", sessions.inventoryApprover());
        assertThat(transfers.findById(id).orElseThrow().getStatus())
                .isEqualTo(WarehouseTransferStatus.READY_TO_TRANSFER);
    }

    void postAs(String path, MockHttpSession session) throws Exception {
        mvc.perform(post(path).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    void decision(String path, MockHttpSession session) throws Exception {
        mvc.perform(post(path).session(session).with(csrf()).param("comment", "HTTP decision"))
                .andExpect(status().is3xxRedirection());
    }

    WarehouseRequestStatus requestStatus(long id) {
        return requests.findById(id).orElseThrow().getStatus();
    }

    long transactionId(long requestId) {
        return jdbc.queryForObject("select id from warehouse_transaction where request_id=?",
                Long.class, requestId);
    }

    long transferId(long requestId) {
        return jdbc.queryForObject("select id from warehouse_transfer where request_id=?",
                Long.class, requestId);
    }

    String codeId(String table, String code) {
        if (!table.equals("operation_type") && !table.equals("reason")) {
            throw new IllegalArgumentException("Unsupported fixture table");
        }
        return jdbc.queryForObject("select id::text from " + table + " where code=?",
                String.class, code);
    }

    long stock(Warehouse warehouse) {
        return jdbc.queryForObject("select coalesce(sum(on_hand_quantity),0) from inventory_lot "
                + "where warehouse_id=? and material_id=? and condition='OLD'",
                Long.class, warehouse.getId(), material.getId());
    }

    void assertActions(AggregateType type, long id, WorkflowAction... expected) {
        assertThat(history.findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(type, id))
                .extracting(StatusHistory::getAction).containsExactly(expected);
    }

    record Sessions(MockHttpSession requester, MockHttpSession other,
            MockHttpSession requestApprover, MockHttpSession staff,
            MockHttpSession inventoryApprover, MockHttpSession keeper) {}
}
