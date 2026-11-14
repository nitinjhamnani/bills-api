package com.b2bfintech.app.service;

import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.BranchMembership;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.util.BranchIdGenerator;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BranchService {

    /** Temporary shared password so a branch user can sign in without the one-time code. */
    public static final String DEFAULT_PASSWORD = "Password@123";

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("CLIENT_ADMIN", "CLIENT_OPERATOR", "BRANCH_USER");

    private final BranchRepository branchRepository;
    private final BranchMembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final ClientService clientService;
    private final TenantUserProvisioningService provisioningService;
    private final PasswordEncoder passwordEncoder;
    private final BillRepository billRepository;

    public BranchService(
            BranchRepository branchRepository,
            BranchMembershipRepository membershipRepository,
            AppUserRepository appUserRepository,
            ClientService clientService,
            TenantUserProvisioningService provisioningService,
            PasswordEncoder passwordEncoder,
            BillRepository billRepository) {
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.clientService = clientService;
        this.provisioningService = provisioningService;
        this.passwordEncoder = passwordEncoder;
        this.billRepository = billRepository;
    }

    /** Gives every branch user the shared password until invite-by-code is the only path again. */
    @PostConstruct
    public void applyDefaultPasswords() {
        for (AppUser user : appUserRepository.findByUserType(AppUser.UserType.CORP_USER)) {
            if (membershipRepository.findBranchIdsByUserId(user.getUserId()).isEmpty()) {
                continue;
            }
            if (user.getPassword() != null && passwordEncoder.matches(DEFAULT_PASSWORD, user.getPassword())) {
                continue;
            }
            provisioningService.setUserPassword(user.getUserId(), DEFAULT_PASSWORD);
        }
    }

    public List<Branch> list(String clientId) {
        requireCorporate(clientId);
        return branchRepository.findBySourceIdOrderByNameAsc(clientId.toString());
    }

    public Branch get(String clientId, String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.ownedBy(clientId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    /** One row per branch: bill count, user count, and whether anyone there can still sign in. */
    public List<BranchOverview> overview(String clientId) {
        Map<String, Long> billCounts = new HashMap<>();
        for (BillRepository.BranchBillCount row : billRepository.countGroupedByBranch(clientId)) {
            billCounts.put(row.getBranchId(), row.getBillCount());
        }
        return list(clientId).stream().map(branch -> {
            List<AppUser> people = users(branch.getId());
            boolean suspended = !people.isEmpty()
                    && people.stream().allMatch(user -> user.getStatus() == AppUser.UserStatus.SUSPENDED);
            return new BranchOverview(
                    branch,
                    people.size(),
                    billCounts.getOrDefault(branch.getId(), 0L),
                    suspended ? "SUSPENDED" : "ACTIVE");
        }).toList();
    }

    public record BranchOverview(Branch branch, int userCount, long managedBillCount, String status) {
    }

    public List<AppUser> users(String branchId) {
        List<String> ids = membershipRepository.findUserIdsByBranchId(branchId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return appUserRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(user -> user.getName() == null ? "" : user.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Corporate users who can be put on a branch. Pass a branch to leave out people already on it. */
    public List<AppUser> pool(String clientId, String excludeBranchId) {
        requireCorporate(clientId);
        Set<String> taken = excludeBranchId == null
                ? Set.of()
                : new HashSet<>(membershipRepository.findUserIdsByBranchId(excludeBranchId));
        return appUserRepository.findByUserOrgId(clientId.toString()).stream()
                .filter(AppUser::isActive)
                .filter(user -> user.getRole() != null && ASSIGNABLE_ROLES.contains(user.getRole().getName()))
                .filter(user -> !taken.contains(user.getUserId()))
                .sorted(Comparator.comparing(user -> user.getName() == null ? "" : user.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<String> branchIdsFor(AuthenticatedUser principal) {
        return membershipRepository.findBranchIdsByUserId(principal.userId());
    }

    public List<Branch> branchesForUser(String clientId, String userId) {
        requireCorporate(clientId);
        Set<String> ids = new HashSet<>(membershipRepository.findBranchIdsByUserId(userId));
        return branchRepository.findBySourceIdOrderByNameAsc(clientId.toString()).stream()
                .filter(branch -> ids.contains(branch.getId()))
                .toList();
    }

    /** Branch users only see bills on branches they belong to. Everyone else sees the corporate. */
    public boolean branchUserCanSee(AuthenticatedUser principal, String billBranchId) {
        if (principal == null || !"BRANCH_USER".equals(principal.role())) {
            return true;
        }
        if (billBranchId == null) {
            return false;
        }
        return membershipRepository.existsByUserIdAndBranchId(principal.userId(), billBranchId);
    }

    public boolean passwordSet(String userId) {
        return appUserRepository.findById(userId)
                .map(user -> user.getPassword() != null && !user.getPassword().isBlank())
                .orElse(false);
    }

    @Transactional
    public Branch create(String clientId, String name, String address, String state, String location, String code,
                         String phoneNumber, String userName, List<String> userIds) {
        Client client = requireActiveCorporate(clientId);
        boolean nameSet = blankToNull(userName) != null;
        boolean phoneSet = phoneNumber != null && !phoneNumber.isBlank();
        if (nameSet != phoneSet) {
            throw new BadRequestException(phoneSet ? "Enter a user name" : "Enter a 10-digit phone number");
        }
        Branch branch = new Branch();
        branch.setBranchId(newBranchId());
        branch.setSourceType(Branch.SourceType.CORP);
        branch.setSourceId(client.getId().toString());
        branch.setName(requiredName(name));
        branch.setAddress(blankToNull(address));
        branch.setState(blankToNull(state));
        branch.setLocation(blankToNull(location));
        branch.setCode(uniqueCode(clientId, code, null));
        branch = branchRepository.save(branch);
        if (userIds != null) {
            for (String userId : userIds) {
                if (userId != null) {
                    assignUser(clientId, branch.getId(), userId);
                }
            }
        }
        if (phoneSet) {
            addUser(client, branch, phoneNumber, userName);
        }
        return branch;
    }

    @Transactional
    public Branch update(String clientId, String branchId, String name, String address, String state, String location, String code) {
        Branch branch = get(clientId, branchId);
        branch.setName(requiredName(name));
        branch.setAddress(blankToNull(address));
        branch.setState(blankToNull(state));
        branch.setLocation(blankToNull(location));
        branch.setCode(uniqueCode(clientId, code, branchId));
        return branchRepository.save(branch);
    }

    @Transactional
    public AppUser addUser(String clientId, String branchId, String phoneNumber, String userName) {
        Client client = requireActiveCorporate(clientId);
        Branch branch = get(clientId, branchId);
        return addUser(client, branch, phoneNumber, userName);
    }

    @Transactional
    public AppUser assignUser(String clientId, String branchId, String userId) {
        requireActiveCorporate(clientId);
        get(clientId, branchId);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.organisationId() == null || !clientId.equals(user.organisationId())) {
            throw new BadRequestException("Choose a user from this corporate");
        }
        if (user.getRole() == null || !ASSIGNABLE_ROLES.contains(user.getRole().getName())) {
            throw new BadRequestException("Choose a user from this corporate");
        }
        if (!user.isActive()) {
            throw new BadRequestException("This person cannot sign in");
        }
        if (membershipRepository.existsByUserIdAndBranchId(userId, branchId)) {
            throw new BadRequestException("This person is already on this branch");
        }
        BranchMembership membership = new BranchMembership();
        membership.setUserId(userId);
        membership.setBranchId(branchId);
        membershipRepository.save(membership);
        return user;
    }

    @Transactional
    public List<AppUser> assignUsers(String clientId, String branchId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BadRequestException("Choose a user");
        }
        List<AppUser> assigned = new ArrayList<>();
        for (String userId : userIds) {
            if (userId != null) {
                assigned.add(assignUser(clientId, branchId, userId));
            }
        }
        if (assigned.isEmpty()) {
            throw new BadRequestException("Choose a user");
        }
        return assigned;
    }

    @Transactional
    public void removeUser(String clientId, String branchId, String userId) {
        get(clientId, branchId);
        BranchMembership membership = membershipRepository.findByUserIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        membershipRepository.delete(membership);
    }

    private AppUser addUser(Client client, Branch branch, String phoneNumber, String userName) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new BadRequestException("Enter a 10-digit phone number");
        }
        String name = blankToNull(userName);
        if (name == null) {
            name = branch.getName();
        }
        AppUser user = provisioningService.createClientUser(
                phoneNumber, name, "BRANCH_USER", client, null, DEFAULT_PASSWORD);
        link(user.getUserId(), branch.getId());
        return user;
    }

    private void link(String userId, String branchId) {
        if (membershipRepository.existsByUserIdAndBranchId(userId, branchId)) {
            return;
        }
        BranchMembership membership = new BranchMembership();
        membership.setUserId(userId);
        membership.setBranchId(branchId);
        membershipRepository.save(membership);
    }

    private Client requireActiveCorporate(String clientId) {
        Client client = requireCorporate(clientId);
        if (client.getStatus() != Client.ClientStatus.ACTIVE) {
            throw new BadRequestException("Branches can be added after the corporate is active");
        }
        return client;
    }

    private Client requireCorporate(String clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() != Client.ClientType.CORPORATE) {
            throw new BadRequestException("A vendor does not have branches");
        }
        return client;
    }

    private String newBranchId() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = BranchIdGenerator.generate();
            if (!branchRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new BadRequestException("Could not assign a branch id");
    }

    private String uniqueCode(String clientId, String code, String currentId) {
        String normalized = blankToNull(code);
        if (normalized == null) {
            return null;
        }
        branchRepository.findBySourceIdAndCode(clientId.toString(), normalized).ifPresent(existing -> {
            if (currentId == null || !currentId.equals(existing.getId())) {
                throw new BadRequestException("Another branch already uses this code");
            }
        });
        return normalized;
    }

    private static String requiredName(String name) {
        String normalized = blankToNull(name);
        if (normalized == null) {
            throw new BadRequestException("Enter a branch name");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
