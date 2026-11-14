package com.b2bfintech.app.controller;

import com.b2bfintech.app.service.BranchService;
import com.b2bfintech.billing.entity.Bill;
import com.b2bfintech.billing.entity.BillPayment;
import com.b2bfintech.billing.repository.BillFetchRepository;
import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.ApiException;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.common.security.ClientScopeGuard;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clients/{clientId}")
public class BranchController {

    private final BranchService branchService;
    private final BillRepository billRepository;
    private final BillFetchRepository billFetchRepository;

    public BranchController(
            BranchService branchService, BillRepository billRepository, BillFetchRepository billFetchRepository) {
        this.branchService = branchService;
        this.billRepository = billRepository;
        this.billFetchRepository = billFetchRepository;
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
    public List<BranchDto> list(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return branchService.list(clientId).stream().map(this::toDto).toList();
    }

    @GetMapping("/branches/{branchId}")
    @PreAuthorize("hasAnyRole('CLIENT_ADMIN','CLIENT_OPERATOR')")
    public BranchDto get(@PathVariable String clientId, @PathVariable String branchId,
                         @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return toDto(branchService.get(clientId, branchId));
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BranchDto create(@PathVariable String clientId, @Valid @RequestBody BranchRequest request,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Branch branch = branchService.create(
                clientId, request.name(), request.address(), request.state(), request.location(), request.code(),
                request.phoneNumber(), request.userName(), request.userIds());
        return toDto(branch);
    }

    @GetMapping("/branches/mine")
    @PreAuthorize("hasRole('BRANCH_USER')")
    public List<BranchDto> mine(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return branchService.branchesForUser(clientId, principal.userId()).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/branch-user-pool")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public List<PoolUserDto> pool(@PathVariable String clientId,
                                  @RequestParam(required = false) String branchId,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return branchService.pool(clientId, branchId).stream().map(PoolUserDto::from).toList();
    }

    @GetMapping("/branches/bulk/template")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public ResponseEntity<byte[]> bulkTemplate(@PathVariable String clientId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) throws IOException {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"branches-upload-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(buildBranchTemplate());
    }

    @PostMapping(value = "/branches/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public List<BillDtos.BulkRowResult> createBulk(@PathVariable String clientId, @RequestParam MultipartFile file,
                                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an Excel file");
        }
        List<BillDtos.BulkRowResult> results = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BadRequestException("The file has no sheet");
            }
            String header = cellText(formatter, sheet.getRow(0), 0);
            if (!header.equalsIgnoreCase("Branch name")) {
                throw new BadRequestException("Use the branch upload template. The first column should be Branch name.");
            }
            int last = Math.min(sheet.getLastRowNum(), 500);
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String name = cellText(formatter, row, 0);
                String address = cellText(formatter, row, 1);
                String code = cellText(formatter, row, 2);
                String userName = cellText(formatter, row, 3);
                String phone = digits(cellText(formatter, row, 4));
                if (name.isEmpty() && address.isEmpty() && code.isEmpty() && userName.isEmpty() && phone.isEmpty()) {
                    continue;
                }
                if (name.equals("Sample Branch") && phone.equals("9000000008")) {
                    continue;
                }
                int rowNum = r + 1;
                try {
                    boolean withUser = !userName.isBlank() || !phone.isBlank();
                    if (withUser && userName.isBlank()) {
                        throw new BadRequestException("Enter a user name");
                    }
                    if (withUser && !phone.matches("\\d{10}")) {
                        throw new BadRequestException("Enter a 10-digit phone number");
                    }
                    Branch created = branchService.create(
                            clientId, name, address, null, null, code,
                            withUser ? phone : null, withUser ? userName : null, List.of());
                    results.add(new BillDtos.BulkRowResult(rowNum, true, created.getName(), null));
                } catch (RuntimeException ex) {
                    results.add(new BillDtos.BulkRowResult(rowNum, false, name, ex.getMessage()));
                }
            }
            if (sheet.getLastRowNum() > 500) {
                results.add(new BillDtos.BulkRowResult(502, false, "", "Only the first 500 rows are created"));
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Could not read the uploaded file. Use the branch template saved as .xlsx.");
        }
        if (results.isEmpty()) {
            throw new BadRequestException("The file has no branches to create");
        }
        return results;
    }

    private static byte[] buildBranchTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Branches");
            String[] headers = {"Branch name", "Branch address", "Branch code", "User name", "User phone"};
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 24 * 256);
            }
            sheet.setDefaultColumnStyle(4, textStyle);
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Sample Branch");
            example.createCell(1).setCellValue("12 Sample Road");
            example.createCell(2).setCellValue("SMP");
            example.createCell(3).setCellValue("Sample User");
            Cell phone = example.createCell(4);
            phone.setCellStyle(textStyle);
            phone.setCellValue("9000000008");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String cellText(DataFormatter formatter, Row row, int index) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    @PatchMapping("/branches/{branchId}")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BranchDto update(@PathVariable String clientId, @PathVariable String branchId,
                            @Valid @RequestBody BranchUpdateRequest request,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        Branch branch = branchService.update(
                clientId, branchId, request.name(), request.address(), request.state(), request.location(), request.code());
        return toDto(branch);
    }

    @PostMapping("/branches/{branchId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public BranchUserDto addUser(@PathVariable String clientId, @PathVariable String branchId,
                                 @Valid @RequestBody BranchUserRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        AppUser user = branchService.addUser(clientId, branchId, request.phoneNumber(), request.userName());
        return BranchUserDto.from(user, branchService.passwordSet(user.getId()));
    }

    @PostMapping("/branches/{branchId}/users/assign")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public List<BranchUserDto> assignUsers(@PathVariable String clientId, @PathVariable String branchId,
                                           @Valid @RequestBody AssignUsersRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        return branchService.assignUsers(clientId, branchId, request.userIds()).stream()
                .map(user -> BranchUserDto.from(user, branchService.passwordSet(user.getId())))
                .toList();
    }

    @DeleteMapping("/branches/{branchId}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public void removeUser(@PathVariable String clientId, @PathVariable String branchId, @PathVariable String userId,
                           @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        branchService.removeUser(clientId, branchId, userId);
    }

    @GetMapping("/branch-home")
    @PreAuthorize("hasRole('BRANCH_USER')")
    public BranchHomeDto home(@PathVariable String clientId, @AuthenticationPrincipal AuthenticatedUser principal) {
        ClientScopeGuard.assertCanAccess(principal, clientId);
        List<Branch> branches = branchService.branchesForUser(clientId, principal.userId());
        if (branches.isEmpty()) {
            return new BranchHomeDto("No branch assigned", 0, 0, BigDecimal.ZERO, 0);
        }
        Set<String> branchIds = branches.stream().map(Branch::getId).collect(Collectors.toSet());
        String name = branches.size() == 1
                ? branches.get(0).getName()
                : branches.stream().map(Branch::getName).collect(Collectors.joining(", "));
        List<Bill> bills = billRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(bill -> branchIds.contains(bill.getBranchId()))
                .toList();
        Set<String> billIds = bills.stream().map(Bill::getId).collect(Collectors.toSet());
        List<com.b2bfintech.billing.entity.BillFetch> pending = billFetchRepository.findDues(clientId).stream()
                .filter(fetch -> billIds.contains(fetch.getBillId()))
                .toList();
        BigDecimal pendingAmount = pending.stream()
                .map(fetch -> fetch.getBillAmount() == null ? BigDecimal.ZERO : fetch.getBillAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BranchHomeDto(name, bills.size(), pending.size(), pendingAmount, branches.size());
    }

    public record BranchRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String address,
            @Size(max = 100) String state,
            @Size(max = 150) String location,
            @Size(max = 40) String code,
            @Size(max = 150) String userName,
            String phoneNumber,
            List<String> userIds) {
    }

    public record AssignUsersRequest(@NotEmpty List<String> userIds) {
    }

    public record PoolUserDto(String id, String fullName, String phoneNumber, String role) {
        static PoolUserDto from(AppUser user) {
            return new PoolUserDto(
                    user.getId().toString(), user.getFullName(), user.getPhoneNumber(), user.getRole().getName());
        }
    }

    public record BranchUpdateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String address,
            @Size(max = 100) String state,
            @Size(max = 150) String location,
            @Size(max = 40) String code) {
    }

    public record BranchUserRequest(
            @NotBlank @Pattern(regexp = "\\d{10}") String phoneNumber,
            @Size(max = 150) String userName) {
    }

    private BranchDto toDto(Branch branch) {
        List<BranchUserDto> users = branchService.users(branch.getId()).stream()
                .map(user -> BranchUserDto.from(user, branchService.passwordSet(user.getId())))
                .toList();
        return new BranchDto(
                branch.getId(), branch.getName(), branch.getAddress(), branch.getState(), branch.getLocation(), branch.getCode(), users);
    }

    public record BranchUserDto(String id, String fullName, String phoneNumber, String role, boolean passwordSet) {
        static BranchUserDto from(AppUser user, boolean passwordSet) {
            return new BranchUserDto(
                    user.getId().toString(), user.getFullName(), user.getPhoneNumber(),
                    user.getRole() == null ? null : user.getRole().getName(), passwordSet);
        }
    }

    public record BranchDto(
            String id, String name, String address, String state, String location, String code, List<BranchUserDto> users) {
    }

    public record BranchHomeDto(
            String branchName, int managedBillCount, int pendingCount, BigDecimal pendingAmount, int branchCount) {
    }
}
