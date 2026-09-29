package com.tttn.qlnvl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tttn.qlnvl.auth.application.AppUserPrincipal;
import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.inventory.repository.InventoryLotRepository;
import com.tttn.qlnvl.shared.demo.DemoWorkflowProvisioner;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import com.tttn.qlnvl.warehouserequest.repository.WarehouseRequestRepository;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransactionStatus;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.demo.password=local-demo-password")
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoWorkflowProvisionerIT extends PostgreSqlIntegrationTestBase {
    @Autowired DemoWorkflowProvisioner provisioner;
    @Autowired AppUserRepository users;
    @Autowired WarehouseRequestService requests;
    @Autowired RequestApprovalService requestApprovals;
    @Autowired WarehouseTransactionService transactions;
    @Autowired TransactionApprovalService transactionApprovals;
    @Autowired TransactionConfirmationService transactionConfirmations;
    @Autowired WarehouseTransferService transfers;
    @Autowired TransferApprovalService transferApprovals;
    @Autowired TransferConfirmationService transferConfirmations;
    @Autowired WarehouseRequestRepository requestRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired InventoryLotRepository inventoryLots;
    @Autowired MockMvc mvc;

    @Test
    void seedsEveryRoleQueueAndRestartIsIdempotent() throws Exception {
        AppUser requester = users.findByUsername("requester_demo").orElseThrow();

        assertThat(requests.searchOwned(requester.getId(), null, null, 0, 20).getContent())
                .hasSizeGreaterThanOrEqualTo(10)
                .extracting(WarehouseRequest::getStatus)
                .contains(WarehouseRequestStatus.DRAFT, WarehouseRequestStatus.SUBMITTED,
                        WarehouseRequestStatus.PROCESSING, WarehouseRequestStatus.COMPLETED);
        assertThat(inventoryLots.count()).isPositive();
        assertThat(requestApprovals.queue(0, 20).getContent()).isNotEmpty();
        assertThat(transactions.queue(0, 20, WarehouseTransactionStatus.DRAFT).getContent())
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(transactionApprovals.queue(0, 20).getContent()).isNotEmpty();
        assertThat(transactionConfirmations.queue(0, 20).getContent()).isNotEmpty();
        assertThat(transfers.queue(0, 20, WarehouseTransferStatus.DRAFT).getContent()).isNotEmpty();
        assertThat(transferApprovals.queue(0, 20).getContent()).isNotEmpty();
        assertThat(transferConfirmations.sourceQueue(0, 20).getContent()).isNotEmpty();
        assertThat(transferConfirmations.destinationQueue(0, 20).getContent()).isNotEmpty();

        assertQueuePagesRenderWithDemoData();

        long requestCount = requestRepository.count();
        long warehouseCount = warehouseRepository.count();
        provisioner.run(null);

        assertThat(requestRepository.count()).isEqualTo(requestCount);
        assertThat(warehouseRepository.count()).isEqualTo(warehouseCount);
    }

    private void assertQueuePagesRenderWithDemoData() throws Exception {
        AppUser requester = demoUser("requester_demo");
        AppUser requestApprover = demoUser("request_approver_demo");
        AppUser inventoryStaff = demoUser("inventory_staff_demo");
        AppUser inventoryApprover = demoUser("inventory_approver_demo");
        AppUser keeper = demoUser("warehouse_keeper_demo");

        assertPage("/requests", requester);
        assertPage("/request-approvals", requestApprover);
        assertPage("/warehouse-transactions", inventoryStaff);
        assertPage("/warehouse-transfers", inventoryStaff);
        assertPage("/transaction-approvals", inventoryApprover);
        assertPage("/transfer-approvals", inventoryApprover);
        assertPage("/transaction-confirmations", keeper);
        assertPage("/transfer-confirmations/source", keeper);
        assertPage("/transfer-confirmations/destination", keeper);
        long destinationTransferId = transferConfirmations.destinationQueue(0, 20)
                .getContent().getFirst().getId();
        assertPage("/transfer-confirmations/destination/" + destinationTransferId, keeper);
    }

    private void assertPage(String path, AppUser actor) throws Exception {
        mvc.perform(get(path).with(user(AppUserPrincipal.from(actor))))
                .andExpect(status().isOk());
    }

    private AppUser demoUser(String username) {
        return users.findByUsername(username).orElseThrow();
    }
}
