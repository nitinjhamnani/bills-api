package com.b2bfintech.app.service;

import com.b2bfintech.billing.repository.BillRepository;
import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.common.security.AuthenticatedUser;
import com.b2bfintech.organisation.entity.AppUser;
import com.b2bfintech.organisation.entity.Branch;
import com.b2bfintech.organisation.entity.BranchMembership;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.UserTypes;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.BranchMembershipRepository;
import com.b2bfintech.organisation.repository.BranchRepository;
import com.b2bfintech.organisation.service.ClientService;
import com.b2bfintech.organisation.util.BranchIdGenerator;
import com.b2bfintech.platform.service.AuthSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchMembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final ClientService clientService;
    private final TenantUserProvisioningService provisioningService;
    private final BillRepository billRepository;
    private final AuthSessionService authSessionService;

    @Autowired
    public BranchService(
            BranchRepository branchRepository,
            BranchMembershipRepository membershipRepository,
            AppUserRepository appUserRepository,
            ClientService clientService,
            TenantUserProvisioningService provisioningService,
            BillRepository billRepository,
            AuthSessionService authSessionService) {
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.clientService = clientService;
        this.provisioningService = provisioningService;
        this.billRepository = billRepository;
        this.authSessionService = authSessionService;
    }

    public List<Branch> list(String clientId) {
        requireCorporate(clientId);
        return branchRepository.findBySourceIdOrderByNameAsc(clientId);
    }

    public Branch get(String clientId, String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.ownedBy(clientId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    public List<BranchOverview> overview(String clientId) {
        Map<String, Long> billCounts = new HashMap<>();
        for (BillRepository.BranchBillCount row : billRepository.countGroupedByBranch(clientId)) {
            billCounts.put(row.getBranchId(), row.getBillCount());
        }
        return list(clientId).stream().map(branch -> {
            List<AppUser> people = users(branch.getId());
            return new BranchOverview(
                    branch,
                    people.size(),
                    billCounts.getOrDefault(branch.getId(), 0L),
                    branch.isActive() ? "ACTIVE" : "SUSPENDED",
                    people);
        }).toList();
    }

    public BranchOverview overviewOne(String clientId, String branchId) {
        Branch branch = get(clientId, branchId);
        List<AppUser> people = users(branch.getId());
        long billCount = billRepository.countGroupedByBranch(clientId).stream()
                .filter(row -> branch.getId().equals(row.getBranchId()))
                .mapToLong(BillRepository.BranchBillCount::getBillCount)
                .findFirst()
                .orElse(0L);
        return new BranchOverview(
                branch,
                people.size(),
                billCount,
                branch.isActive() ? "ACTIVE" : "SUSPENDED",
                people);
    }

    public record BranchOverview(
            Branch branch, int userCount, long managedBillCount, String status, List<AppUser> users) {
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

    /** Corporate non-admin users who can be assigned to a branch. */
    public List<AppUser> pool(String clientId, String excludeBranchId) {
        requireCorporate(clientId);
        Set<String> taken = excludeBranchId == null
                ? Set.of()
                : new HashSet<>(membershipRepository.findUserIdsByBranchId(excludeBranchId));
        return appUserRepository.findByUserOrgId(clientId).stream()
                .filter(AppUser::isActive)
                .filter(user -> user.hasUserType(AppUser.UserType.CORP_USER))
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
        return branchRepository.findBySourceIdOrderByNameAsc(clientId).stream()
                .filter(branch -> ids.contains(branch.getId()))
                .toList();
    }

    @Transactional
    public List<Branch> setBranchesForUser(String clientId, String userId, List<String> requestedBranchIds) {
        requireActiveCorporate(clientId);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.organisationId() == null || !clientId.equals(user.organisationId())) {
            throw new BadRequestException("Choose a user from this corporate");
        }
        if (!user.hasUserType(AppUser.UserType.CORP_USER)) {
            throw new BadRequestException("Only branch users can be assigned to a branch");
        }
        Set<String> targetIds = new HashSet<>();
        if (requestedBranchIds != null) {
            for (String raw : requestedBranchIds) {
                if (raw != null && !raw.isBlank()) {
                    String bid = raw.trim();
                    get(clientId, bid);
                    targetIds.add(bid);
                }
            }
        }
        List<String> currentIds = membershipRepository.findBranchIdsByUserId(userId);
        boolean wasEmpty = currentIds.isEmpty();
        for (String currentId : currentIds) {
            if (!targetIds.contains(currentId)) {
                membershipRepository.findByUserIdAndBranchId(userId, currentId)
                        .ifPresent(membershipRepository::delete);
            }
        }
        for (String targetId : targetIds) {
            if (!currentIds.contains(targetId)) {
                link(userId, targetId);
            }
        }
        if (wasEmpty != targetIds.isEmpty() && authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "BRANCH_ROLE_CHANGED");
        }
        return branchesForUser(clientId, userId);
    }

    @Transactional
    public List<AppUser> setUsersForBranch(String clientId, String branchId, List<String> requestedUserIds) {
        requireActiveCorporate(clientId);
        get(clientId, branchId);
        Set<String> targetUserIds = new HashSet<>();
        if (requestedUserIds != null) {
            for (String raw : requestedUserIds) {
                if (raw != null && !raw.isBlank()) {
                    String uid = raw.trim();
                    AppUser user = appUserRepository.findById(uid)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    if (user.organisationId() == null || !clientId.equals(user.organisationId())) {
                        throw new BadRequestException("Choose a branch user from this corporate");
                    }
                    if (!user.hasUserType(AppUser.UserType.CORP_USER)) {
                        throw new BadRequestException("Only branch users can be assigned to a branch");
                    }
                    targetUserIds.add(uid);
                }
            }
        }
        List<String> currentUserIds = membershipRepository.findUserIdsByBranchId(branchId);
        Set<String> touchedUsers = new HashSet<>();
        for (String currentId : currentUserIds) {
            if (!targetUserIds.contains(currentId)) {
                membershipRepository.findByUserIdAndBranchId(currentId, branchId)
                        .ifPresent(membershipRepository::delete);
                touchedUsers.add(currentId);
            }
        }
        for (String targetId : targetUserIds) {
            if (!currentUserIds.contains(targetId)) {
                link(targetId, branchId);
                touchedUsers.add(targetId);
            }
        }
        if (authSessionService != null) {
            for (String uid : touchedUsers) {
                authSessionService.revokeAllForUser(uid, "BRANCH_MEMBERSHIP_CHANGED");
            }
        }
        return users(branchId);
    }

    @Transactional
    public Branch setBranchActive(String clientId, String branchId, boolean active) {
        Branch branch = get(clientId, branchId);
        branch.setActive(active);
        return branchRepository.save(branch);
    }

    public String screenRole(AppUser user) {
        if (user == null || user.getUserType() == null) {
            return null;
        }
        if (user.hasUserType(AppUser.UserType.CORP_USER)) {
            boolean hasBranch = !membershipRepository.findBranchIdsByUserId(user.getUserId()).isEmpty();
            return UserTypes.tokenRole(user.getUserType(), hasBranch);
        }
        return UserTypes.defaultRole(user.getUserType());
    }

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
        return create(clientId, name, address, state, location, code, phoneNumber, userName, null, userIds);
    }

    @Transactional
    public Branch create(String clientId, String name, String address, String state, String location, String code,
                         String phoneNumber, String userName, String userPassword, List<String> userIds) {
        Client client = requireActiveCorporate(clientId);
        boolean nameSet = blankToNull(userName) != null;
        boolean phoneSet = phoneNumber != null && !phoneNumber.isBlank();
        boolean passwordProvided = userPassword != null && !userPassword.isBlank();
        if (nameSet || phoneSet || passwordProvided) {
            if (!nameSet) {
                throw new BadRequestException("Enter a user name");
            }
            if (!phoneSet) {
                throw new BadRequestException("Enter a 10-digit phone number");
            }
            if (!passwordProvided) {
                throw new BadRequestException("Enter an initial password (at least 6 characters) for the new user");
            }
        }
        Branch branch = new Branch();
        branch.setBranchId(newBranchId());
        branch.setSourceType(Branch.SourceType.CORP);
        branch.setSourceId(client.getId());
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
            addUser(client, branch, phoneNumber, userName, userPassword);
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
    public AppUser addUser(String clientId, String branchId, String phoneNumber, String userName, String userPassword) {
        Client client = requireActiveCorporate(clientId);
        Branch branch = get(clientId, branchId);
        return addUser(client, branch, phoneNumber, userName, userPassword);
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
        if (!user.hasUserType(AppUser.UserType.CORP_USER)) {
            throw new BadRequestException("Only corporate branch/operator users can be assigned to a branch");
        }
        if (!user.isActive()) {
            throw new BadRequestException("This person cannot sign in");
        }
        if (membershipRepository.existsByUserIdAndBranchId(userId, branchId)) {
            throw new BadRequestException("This person is already on this branch");
        }
        boolean wasEmpty = membershipRepository.findBranchIdsByUserId(userId).isEmpty();
        BranchMembership membership = new BranchMembership();
        membership.setUserId(userId);
        membership.setBranchId(branchId);
        membershipRepository.save(membership);
        if (wasEmpty && authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "BRANCH_ROLE_CHANGED");
        }
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
        if (membershipRepository.findBranchIdsByUserId(userId).isEmpty() && authSessionService != null) {
            authSessionService.revokeAllForUser(userId, "BRANCH_ROLE_CHANGED");
        }
    }

    private AppUser addUser(Client client, Branch branch, String phoneNumber, String userName, String userPassword) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new BadRequestException("Enter a 10-digit phone number");
        }
        String name = blankToNull(userName);
        if (name == null) {
            name = branch.getName();
        }
        AppUser user = provisioningService.createClientUser(
                phoneNumber, name, "BRANCH_USER", client, null, userPassword);
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
        branchRepository.findBySourceIdAndCode(clientId, normalized).ifPresent(existing -> {
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
