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
import com.b2bfintech.platform.model.UserDirectoryEntry;
import com.b2bfintech.platform.repository.UserDirectoryRepository;
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
    private final UserDirectoryRepository userDirectoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final BillRepository billRepository;

    public BranchService(
            BranchRepository branchRepository,
            BranchMembershipRepository membershipRepository,
            AppUserRepository appUserRepository,
            ClientService clientService,
            TenantUserProvisioningService provisioningService,
            UserDirectoryRepository userDirectoryRepository,
            PasswordEncoder passwordEncoder,
            BillRepository billRepository) {
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.clientService = clientService;
        this.provisioningService = provisioningService;
        this.userDirectoryRepository = userDirectoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.billRepository = billRepository;
    }

    /** Gives every branch user the shared password until invite-by-code is the only path again. */
    @PostConstruct
    public void applyDefaultPasswords() {
        for (UserDirectoryEntry entry : userDirectoryRepository.findByRole("BRANCH_USER")) {
            if (entry.passwordHash() != null && passwordEncoder.matches(DEFAULT_PASSWORD, entry.passwordHash())) {
                continue;
            }
            provisioningService.setUserPassword(entry.userId(), DEFAULT_PASSWORD);
        }
    }

    public List<Branch> list(UUID clientId) {
        requireCorporate(clientId);
        return branchRepository.findByClientIdOrderByNameAsc(clientId);
    }

    public Branch get(UUID clientId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    /** One row per branch: bill count, user count, and whether anyone there can still sign in. */
    public List<BranchOverview> overview(UUID clientId) {
        Map<UUID, Long> billCounts = new HashMap<>();
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

    public List<AppUser> users(UUID branchId) {
        List<UUID> ids = membershipRepository.findUserIdsByBranchId(branchId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return appUserRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(user -> user.getFullName() == null ? "" : user.getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Corporate users who can be put on a branch. Pass a branch to leave out people already on it. */
    public List<AppUser> pool(UUID clientId, UUID excludeBranchId) {
        requireCorporate(clientId);
        Set<UUID> taken = excludeBranchId == null
                ? Set.of()
                : new HashSet<>(membershipRepository.findUserIdsByBranchId(excludeBranchId));
        return appUserRepository.findByClientId(clientId).stream()
                .filter(user -> user.getStatus() == AppUser.UserStatus.ACTIVE)
                .filter(user -> user.getRole() != null && ASSIGNABLE_ROLES.contains(user.getRole().getName()))
                .filter(user -> !taken.contains(user.getId()))
                .sorted(Comparator.comparing(user -> user.getFullName() == null ? "" : user.getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<UUID> branchIdsFor(AuthenticatedUser principal) {
        return membershipRepository.findBranchIdsByUserId(UUID.fromString(principal.userId()));
    }

    public List<Branch> branchesForUser(UUID clientId, UUID userId) {
        requireCorporate(clientId);
        Set<UUID> ids = new HashSet<>(membershipRepository.findBranchIdsByUserId(userId));
        return branchRepository.findByClientIdOrderByNameAsc(clientId).stream()
                .filter(branch -> ids.contains(branch.getId()))
                .toList();
    }

    /** Branch users only see bills on branches they belong to. Everyone else sees the corporate. */
    public boolean branchUserCanSee(AuthenticatedUser principal, UUID billBranchId) {
        if (principal == null || !"BRANCH_USER".equals(principal.role())) {
            return true;
        }
        if (billBranchId == null) {
            return false;
        }
        return membershipRepository.existsByUserIdAndBranchId(UUID.fromString(principal.userId()), billBranchId);
    }

    public boolean passwordSet(UUID userId) {
        return userDirectoryRepository.hasPassword(userId);
    }

    @Transactional
    public Branch create(UUID clientId, String name, String address, String code, String comment,
                         String phoneNumber, String userName, List<UUID> userIds) {
        Client client = requireActiveCorporate(clientId);
        boolean nameSet = blankToNull(userName) != null;
        boolean phoneSet = phoneNumber != null && !phoneNumber.isBlank();
        if (nameSet != phoneSet) {
            throw new BadRequestException(phoneSet ? "Enter a user name" : "Enter a 10-digit phone number");
        }
        Branch branch = new Branch();
        branch.setClientId(clientId);
        branch.setName(requiredName(name));
        branch.setAddress(blankToNull(address));
        branch.setCode(uniqueCode(clientId, code, null));
        branch.setComment(blankToNull(comment));
        branch = branchRepository.save(branch);
        if (userIds != null) {
            for (UUID userId : userIds) {
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
    public Branch update(UUID clientId, UUID branchId, String name, String address, String code, String comment) {
        Branch branch = get(clientId, branchId);
        branch.setName(requiredName(name));
        branch.setAddress(blankToNull(address));
        branch.setCode(uniqueCode(clientId, code, branchId));
        branch.setComment(blankToNull(comment));
        return branchRepository.save(branch);
    }

    @Transactional
    public AppUser addUser(UUID clientId, UUID branchId, String phoneNumber, String userName) {
        Client client = requireActiveCorporate(clientId);
        Branch branch = get(clientId, branchId);
        return addUser(client, branch, phoneNumber, userName);
    }

    @Transactional
    public AppUser assignUser(UUID clientId, UUID branchId, UUID userId) {
        requireActiveCorporate(clientId);
        get(clientId, branchId);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getClient() == null || !clientId.equals(user.getClient().getId())) {
            throw new BadRequestException("Choose a user from this corporate");
        }
        if (user.getRole() == null || !ASSIGNABLE_ROLES.contains(user.getRole().getName())) {
            throw new BadRequestException("Choose a user from this corporate");
        }
        if (user.getStatus() != AppUser.UserStatus.ACTIVE) {
            throw new BadRequestException("This person cannot sign in");
        }
        if (membershipRepository.existsByUserIdAndBranchId(userId, branchId)) {
            throw new BadRequestException("This person is already on this branch");
        }
        BranchMembership membership = new BranchMembership();
        membership.setUserId(userId);
        membership.setBranchId(branchId);
        membershipRepository.save(membership);
        rememberBranch(user, branchId);
        return user;
    }

    @Transactional
    public List<AppUser> assignUsers(UUID clientId, UUID branchId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BadRequestException("Choose a user");
        }
        List<AppUser> assigned = new ArrayList<>();
        for (UUID userId : userIds) {
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
    public void removeUser(UUID clientId, UUID branchId, UUID userId) {
        get(clientId, branchId);
        BranchMembership membership = membershipRepository.findByUserIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        membershipRepository.delete(membership);
        membershipRepository.flush();
        AppUser user = appUserRepository.findById(userId).orElse(null);
        if (user == null || !"BRANCH_USER".equals(user.getRole().getName()) || !branchId.equals(user.getBranchId())) {
            return;
        }
        UUID next = membershipRepository.findBranchIdsByUserId(userId).stream()
                .filter(id -> !branchId.equals(id))
                .findFirst()
                .orElse(null);
        user.setBranchId(next);
        appUserRepository.save(user);
        userDirectoryRepository.updateBranchId(user.getId(), next);
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
        user.setBranchId(branch.getId());
        user = appUserRepository.save(user);
        userDirectoryRepository.updateBranchId(user.getId(), branch.getId());
        link(user.getId(), branch.getId());
        return user;
    }

    private void link(UUID userId, UUID branchId) {
        if (membershipRepository.existsByUserIdAndBranchId(userId, branchId)) {
            return;
        }
        BranchMembership membership = new BranchMembership();
        membership.setUserId(userId);
        membership.setBranchId(branchId);
        membershipRepository.save(membership);
    }

    /** Keeps a single branch id on a branch user so login still has one. Other roles are not narrowed. */
    private void rememberBranch(AppUser user, UUID branchId) {
        if (user.getRole() == null || !"BRANCH_USER".equals(user.getRole().getName()) || user.getBranchId() != null) {
            return;
        }
        user.setBranchId(branchId);
        appUserRepository.save(user);
        userDirectoryRepository.updateBranchId(user.getId(), branchId);
    }

    private Client requireActiveCorporate(UUID clientId) {
        Client client = requireCorporate(clientId);
        if (client.getStatus() != Client.ClientStatus.ACTIVE) {
            throw new BadRequestException("Branches can be added after the corporate is active");
        }
        return client;
    }

    private Client requireCorporate(UUID clientId) {
        Client client = clientService.getById(clientId);
        if (client.getClientType() != Client.ClientType.CORPORATE) {
            throw new BadRequestException("A vendor does not have branches");
        }
        return client;
    }

    private String uniqueCode(UUID clientId, String code, UUID currentId) {
        String normalized = blankToNull(code);
        if (normalized == null) {
            return null;
        }
        branchRepository.findByClientIdAndCode(clientId, normalized).ifPresent(existing -> {
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
