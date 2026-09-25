package com.tttn.qlnvl.shared.demo;

import com.tttn.qlnvl.auth.domain.AppUser;
import com.tttn.qlnvl.auth.repository.AppUserRepository;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.application.RequestApprovalService;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestDraftCommand;
import com.tttn.qlnvl.warehouserequest.application.WarehouseRequestService;
import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import com.tttn.qlnvl.warehousetransaction.application.TransactionApprovalService;
import com.tttn.qlnvl.warehousetransaction.application.TransactionConfirmationService;
import com.tttn.qlnvl.warehousetransaction.application.WarehouseTransactionService;
import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import com.tttn.qlnvl.warehousetransaction.repository.WarehouseTransactionRepository;
import com.tttn.qlnvl.warehousetransfer.application.TransferApprovalService;
import com.tttn.qlnvl.warehousetransfer.application.TransferConfirmationService;
import com.tttn.qlnvl.warehousetransfer.application.WarehouseTransferService;
import com.tttn.qlnvl.warehousetransfer.domain.WarehouseTransfer;
import com.tttn.qlnvl.warehousetransfer.repository.WarehouseTransferRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(30)
public class DemoWorkflowProvisioner implements ApplicationRunner {
    static final String SOURCE_WAREHOUSE_CODE = "DEMO_KHO_NGUON";
    static final String DESTINATION_WAREHOUSE_CODE = "DEMO_KHO_DICH";
    private static final String MATERIAL_CODE = "CARD_OTHER_DEMO";
    private static final long SCENARIO_QUANTITY = 5L;

    private final AppUserRepository users;
    private final MaterialRepository materials;
    private final WarehouseRepository warehouses;
    private final OperationTypeRepository operationTypes;
    private final ReasonRepository reasons;
    private final WarehouseRequestService requestService;
    private final RequestApprovalService requestApprovals;
    private final WarehouseTransactionRepository transactions;
    private final WarehouseTransactionService transactionService;
    private final TransactionApprovalService transactionApprovals;
    private final TransactionConfirmationService transactionConfirmations;
    private final WarehouseTransferRepository transfers;
    private final WarehouseTransferService transferService;
    private final TransferApprovalService transferApprovals;
    private final TransferConfirmationService transferConfirmations;

    public DemoWorkflowProvisioner(AppUserRepository users, MaterialRepository materials,
            WarehouseRepository warehouses, OperationTypeRepository operationTypes,
            ReasonRepository reasons,
            WarehouseRequestService requestService, RequestApprovalService requestApprovals,
            WarehouseTransactionRepository transactions,
            WarehouseTransactionService transactionService,
            TransactionApprovalService transactionApprovals,
            TransactionConfirmationService transactionConfirmations,
            WarehouseTransferRepository transfers, WarehouseTransferService transferService,
            TransferApprovalService transferApprovals,
            TransferConfirmationService transferConfirmations) {
        this.users = users;
        this.materials = materials;
        this.warehouses = warehouses;
        this.operationTypes = operationTypes;
        this.reasons = reasons;
        this.requestService = requestService;
        this.requestApprovals = requestApprovals;
        this.transactions = transactions;
        this.transactionService = transactionService;
        this.transactionApprovals = transactionApprovals;
        this.transactionConfirmations = transactionConfirmations;
        this.transfers = transfers;
        this.transferService = transferService;
        this.transferApprovals = transferApprovals;
        this.transferConfirmations = transferConfirmations;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (warehouses.existsByWarehouseCodeIgnoreCase(SOURCE_WAREHOUSE_CODE)) {
            return;
        }

        DemoActors actors = actors();
        Material material = material();
        Warehouse source = warehouses.saveAndFlush(new Warehouse(SOURCE_WAREHOUSE_CODE,
                "Kho demo nghiệp vụ nguồn", "Địa chỉ kho demo nguồn", null, actors.keeper()));
        Warehouse destination = warehouses.saveAndFlush(new Warehouse(DESTINATION_WAREHOUSE_CODE,
                "Kho demo nghiệp vụ đích", "Địa chỉ kho demo đích", null, actors.keeper()));

        OperationType importOperation = operation("IMP_OTHER");
        OperationType exportOperation = operation("EXP_OTHER");
        OperationType transferOperation = operation("WWT");
        Reason importReason = reason("IMPORT_RETURN");
        Reason exportReason = reason("EXPORT_USE");
        Reason transferReason = reason("TRANSFER_INTERNAL");

        WarehouseRequest openingRequest = submitRequest(importOperation, importReason, null,
                source, material, actors.requester(), "Demo opening receipt", 500L);
        requestApprovals.approve(openingRequest.getId(), actors.requestApprover().getId(),
                "Duyệt tồn khởi tạo demo");
        WarehouseTransaction openingTransaction = transactions.findByRequestId(
                        openingRequest.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Demo opening transaction was not created"));
        transactionService.submit(openingTransaction.getId(), actors.inventoryStaff().getId(),
                "Nhập tồn khởi tạo demo");
        transactionApprovals.approve(openingTransaction.getId(),
                actors.inventoryApprover().getId(), "Duyệt tồn khởi tạo demo");
        transactionConfirmations.confirm(openingTransaction.getId(), actors.keeper().getId());

        createRequest(importOperation, importReason, null, destination, material,
                actors.requester(), "Demo Request DRAFT");
        submitRequest(exportOperation, exportReason, source, null, material,
                actors.requester(), "Demo Request SUBMITTED");

        approvedTransaction(importOperation, importReason, null, destination, material, actors,
                "Demo Receipt DRAFT");
        approvedTransaction(exportOperation, exportReason, source, null, material, actors,
                "Demo Issue DRAFT");

        WarehouseTransaction submittedTransaction = approvedTransaction(importOperation,
                importReason, null, destination, material, actors, "Demo Transaction SUBMITTED");
        transactionService.submit(submittedTransaction.getId(), actors.inventoryStaff().getId(),
                "Dữ liệu demo chờ duyệt kho");

        WarehouseTransaction readyTransaction = approvedTransaction(importOperation,
                importReason, null, destination, material, actors,
                "Demo Transaction READY_FOR_CONFIRMATION");
        transactionService.submit(readyTransaction.getId(), actors.inventoryStaff().getId(),
                "Dữ liệu demo chờ xác nhận vật lý");
        transactionApprovals.approve(readyTransaction.getId(),
                actors.inventoryApprover().getId(), "Duyệt dữ liệu demo");

        approvedTransfer(transferOperation, transferReason, source, destination, material, actors,
                "Demo Transfer DRAFT");

        WarehouseTransfer submittedTransfer = approvedTransfer(transferOperation, transferReason,
                source, destination, material, actors, "Demo Transfer SUBMITTED");
        transferService.submit(submittedTransfer.getId(), actors.inventoryStaff().getId());

        WarehouseTransfer readyTransfer = approvedTransfer(transferOperation, transferReason,
                source, destination, material, actors, "Demo Transfer READY_TO_TRANSFER");
        transferService.submit(readyTransfer.getId(), actors.inventoryStaff().getId());
        transferApprovals.approve(readyTransfer.getId(), actors.inventoryApprover().getId(),
                "Duyệt điều chuyển demo");

        WarehouseTransfer inTransitTransfer = approvedTransfer(transferOperation, transferReason,
                source, destination, material, actors, "Demo Transfer IN_TRANSIT");
        transferService.submit(inTransitTransfer.getId(), actors.inventoryStaff().getId());
        transferApprovals.approve(inTransitTransfer.getId(), actors.inventoryApprover().getId(),
                "Duyệt điều chuyển demo");
        transferConfirmations.confirmSource(inTransitTransfer.getId(), actors.keeper().getId());
    }

    private WarehouseRequest createRequest(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, AppUser requester,
            String note) {
        return createRequest(operation, reason, source, destination, material, requester, note,
                SCENARIO_QUANTITY);
    }

    private WarehouseRequest createRequest(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, AppUser requester,
            String note, long quantity) {
        return requestService.createDraft(new WarehouseRequestDraftCommand(
                operation.getId(), reason.getId(), source == null ? null : source.getId(),
                destination == null ? null : destination.getId(), null, note,
                List.of(new WarehouseRequestDraftCommand.Detail(material.getId(),
                        MaterialCondition.OLD, quantity, null))), requester.getId());
    }

    private WarehouseRequest submitRequest(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, AppUser requester,
            String note) {
        return submitRequest(operation, reason, source, destination, material, requester, note,
                SCENARIO_QUANTITY);
    }

    private WarehouseRequest submitRequest(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, AppUser requester,
            String note, long quantity) {
        WarehouseRequest request = createRequest(operation, reason, source, destination,
                material, requester, note, quantity);
        return requestService.submit(request.getId(), requester.getId());
    }

    private WarehouseTransaction approvedTransaction(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, DemoActors actors,
            String note) {
        WarehouseRequest request = submitRequest(operation, reason, source, destination,
                material, actors.requester(), note);
        requestApprovals.approve(request.getId(), actors.requestApprover().getId(),
                "Duyệt Request demo");
        return transactions.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Demo transaction was not created for request " + request.getId()));
    }

    private WarehouseTransfer approvedTransfer(OperationType operation, Reason reason,
            Warehouse source, Warehouse destination, Material material, DemoActors actors,
            String note) {
        WarehouseRequest request = submitRequest(operation, reason, source, destination,
                material, actors.requester(), note);
        requestApprovals.approve(request.getId(), actors.requestApprover().getId(),
                "Duyệt Request demo");
        return transfers.findByRequestId(request.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Demo transfer was not created for request " + request.getId()));
    }

    private DemoActors actors() {
        return new DemoActors(user("requester_demo"), user("request_approver_demo"),
                user("inventory_staff_demo"), user("inventory_approver_demo"),
                user("warehouse_keeper_demo"));
    }

    private AppUser user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Missing demo user: " + username));
    }

    private Material material() {
        return materials.findByMaterialCode(MATERIAL_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing demo material: " + MATERIAL_CODE));
    }

    private OperationType operation(String code) {
        return operationTypes.findAll().stream()
                .filter(candidate -> candidate.getCode().equals(code) && candidate.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing accepted operation type: " + code));
    }

    private Reason reason(String code) {
        return reasons.findAll().stream()
                .filter(candidate -> candidate.getCode().equals(code) && candidate.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing accepted reason: " + code));
    }

    private record DemoActors(AppUser requester, AppUser requestApprover,
            AppUser inventoryStaff, AppUser inventoryApprover, AppUser keeper) {
    }
}
