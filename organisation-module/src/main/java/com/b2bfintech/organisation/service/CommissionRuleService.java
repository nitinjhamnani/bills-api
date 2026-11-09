package com.b2bfintech.organisation.service;

import com.b2bfintech.common.exception.BadRequestException;
import com.b2bfintech.common.exception.ResourceNotFoundException;
import com.b2bfintech.organisation.entity.Client;
import com.b2bfintech.organisation.entity.CommissionAmountRange;
import com.b2bfintech.organisation.entity.CommissionRateProposal;
import com.b2bfintech.organisation.entity.CommissionRule;
import com.b2bfintech.organisation.entity.CommissionRuleHistory;
import com.b2bfintech.organisation.entity.CommissionRuleVendor;
import com.b2bfintech.organisation.repository.CommissionAmountRangeRepository;
import com.b2bfintech.organisation.repository.CommissionRateProposalRepository;
import com.b2bfintech.organisation.repository.CommissionRuleHistoryRepository;
import com.b2bfintech.organisation.repository.CommissionRuleRepository;
import com.b2bfintech.organisation.repository.AppUserRepository;
import com.b2bfintech.organisation.repository.CommissionRuleVendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommissionRuleService {

    private final CommissionRuleRepository ruleRepository;
    private final CommissionRuleVendorRepository vendorRepository;
    private final CommissionRuleHistoryRepository historyRepository;
    private final CommissionAmountRangeRepository rangeRepository;
    private final CommissionRateProposalRepository proposalRepository;
    private final ClientService clientService;
    private final AppUserRepository appUserRepository;

    public CommissionRuleService(
            CommissionRuleRepository ruleRepository,
            CommissionRuleVendorRepository vendorRepository,
            CommissionRuleHistoryRepository historyRepository,
            CommissionAmountRangeRepository rangeRepository,
            CommissionRateProposalRepository proposalRepository,
            ClientService clientService,
            AppUserRepository appUserRepository) {
        this.ruleRepository = ruleRepository;
        this.vendorRepository = vendorRepository;
        this.historyRepository = historyRepository;
        this.rangeRepository = rangeRepository;
        this.proposalRepository = proposalRepository;
        this.clientService = clientService;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list(UUID vendorClientId) {
        List<CommissionRule> rules = ruleRepository.findAllByOrderByCreatedAtAsc();
        if (vendorClientId != null) {
            Set<UUID> included = vendorRepository.findByVendorClientId(vendorClientId).stream()
                    .map(CommissionRuleVendor::getRuleId)
                    .collect(Collectors.toSet());
            rules = rules.stream().filter(rule -> included.contains(rule.getId())).toList();
        }
        List<RuleView> views = new ArrayList<>(toViews(rules));
        views.sort(Comparator.comparing(RuleView::active).reversed()
                .thenComparing(RuleView::highestCommissionPercent, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RuleView::createdAt));
        return views;
    }

    @Transactional(readOnly = true)
    public List<VendorRuleView> listForVendor(UUID vendorClientId) {
        Set<UUID> ruleIds = vendorRepository.findByVendorClientId(vendorClientId).stream()
                .map(CommissionRuleVendor::getRuleId)
                .collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return List.of();
        }
        List<CommissionRule> rules = ruleRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(rule -> rule.isActive() && ruleIds.contains(rule.getId()))
                .toList();
        Map<UUID, CommissionRuleVendor> mine = vendorRepository.findByVendorClientId(vendorClientId).stream()
                .collect(Collectors.toMap(CommissionRuleVendor::getRuleId, Function.identity()));
        Map<UUID, CommissionRateProposal> pending = pendingFor(rules.stream().map(CommissionRule::getId).toList()).stream()
                .filter(proposal -> vendorClientId.equals(proposal.getVendorClientId()))
                .collect(Collectors.toMap(CommissionRateProposal::getRuleId, Function.identity(), (a, b) -> a));
        List<VendorRuleView> views = new ArrayList<>();
        for (CommissionRule rule : rules) {
            CommissionRuleVendor seat = mine.get(rule.getId());
            if (seat == null) {
                continue;
            }
            CommissionRateProposal proposal = pending.get(rule.getId());
            views.add(new VendorRuleView(
                    rule.getId(),
                    rule.getCategoryId(),
                    rule.getCategoryName(),
                    rule.getProviderBillerId(),
                    rule.getProviderName(),
                    rangeView(rule.getRange()),
                    seat.getCommissionPercent(),
                    seat.isActive(),
                    proposal == null ? null : proposal.getId(),
                    proposal == null ? null : proposal.getProposedPercent(),
                    rule.getCreatedAt()));
        }
        views.sort(Comparator.comparing(VendorRuleView::createdAt));
        return views;
    }

    @Transactional(readOnly = true)
    public RuleDetail get(UUID ruleId) {
        CommissionRule rule = requireRule(ruleId);
        return new RuleDetail(toViews(List.of(rule)).get(0), history(ruleId));
    }

    @Transactional
    public RuleDetail create(NewRule request, String actor) {
        if (request.vendors() == null || request.vendors().isEmpty()) {
            throw new BadRequestException("Select at least one vendor");
        }
        CommissionAmountRange range = requireFreeRange(request.rangeId());
        List<PreparedVendor> prepared = prepareVendors(request.vendors());
        CommissionRule rule = new CommissionRule();
        rule.setCategoryId(request.categoryId().trim());
        rule.setCategoryName(request.categoryName().trim());
        rule.setProviderBillerId(request.providerBillerId().trim());
        rule.setProviderName(request.providerName().trim());
        rule.setRange(range);
        rule.setActive(true);
        rule = ruleRepository.save(rule);
        for (PreparedVendor vendor : prepared) {
            CommissionRuleVendor seat = new CommissionRuleVendor();
            seat.setRuleId(rule.getId());
            seat.setVendorClientId(vendor.client().getId());
            seat.setCommissionPercent(vendor.percent());
            seat.setActive(true);
            vendorRepository.save(seat);
            record(rule.getId(), vendor.client().getId(), CommissionRuleHistory.EventType.VENDOR_ADDED, null, actor);
            if (vendor.percent() != null) {
                record(rule.getId(), vendor.client().getId(), CommissionRuleHistory.EventType.RATE_ENTERED, vendor.percent(), actor);
            }
        }
        return get(rule.getId());
    }

    @Transactional
    public RuleDetail addVendor(UUID ruleId, UUID vendorClientId, BigDecimal commissionPercent, String actor) {
        CommissionRule rule = requireRule(ruleId);
        Client vendor = requireSelectableVendor(vendorClientId);
        if (vendorRepository.findByRuleIdAndVendorClientId(rule.getId(), vendor.getId()).isPresent()) {
            throw new BadRequestException("This vendor is already on the rule");
        }
        BigDecimal percent = commissionPercent == null ? null : percent(commissionPercent);
        if (percent != null) {
            assertPercentFree(rule.getId(), vendor.getId(), percent);
        }
        CommissionRuleVendor seat = new CommissionRuleVendor();
        seat.setRuleId(rule.getId());
        seat.setVendorClientId(vendor.getId());
        seat.setCommissionPercent(percent);
        seat.setActive(true);
        vendorRepository.save(seat);
        record(rule.getId(), vendor.getId(), CommissionRuleHistory.EventType.VENDOR_ADDED, null, actor);
        if (percent != null) {
            record(rule.getId(), vendor.getId(), CommissionRuleHistory.EventType.RATE_ENTERED, percent, actor);
        }
        return get(rule.getId());
    }

    @Transactional
    public RuleDetail removeVendor(UUID ruleId, UUID vendorClientId, String actor) {
        CommissionRuleVendor seat = requireSeat(ruleId, vendorClientId);
        proposalRepository.deleteByRuleIdAndVendorClientId(ruleId, vendorClientId);
        vendorRepository.delete(seat);
        record(ruleId, vendorClientId, CommissionRuleHistory.EventType.VENDOR_REMOVED, seat.getCommissionPercent(), actor);
        return get(ruleId);
    }

    @Transactional
    public RuleDetail setVendorActive(UUID ruleId, UUID vendorClientId, boolean active, String actor) {
        CommissionRuleVendor seat = requireSeat(ruleId, vendorClientId);
        if (seat.isActive() != active) {
            seat.setActive(active);
            vendorRepository.save(seat);
            record(ruleId, vendorClientId,
                    active ? CommissionRuleHistory.EventType.VENDOR_RESUMED : CommissionRuleHistory.EventType.VENDOR_PAUSED,
                    seat.getCommissionPercent(), actor);
        }
        return get(ruleId);
    }

    @Transactional
    public RuleDetail enterRate(UUID ruleId, UUID vendorClientId, BigDecimal commissionPercent, String actor) {
        CommissionRuleVendor seat = requireSeat(ruleId, vendorClientId);
        BigDecimal next = percent(commissionPercent);
        if (seat.getCommissionPercent() != null && seat.getCommissionPercent().compareTo(next) == 0) {
            throw new BadRequestException("That is already this vendor's commission");
        }
        proposalRepository.findByRuleIdAndVendorClientIdAndStatus(ruleId, vendorClientId, CommissionRateProposal.Status.PENDING)
                .ifPresent(proposal -> {
                    if (proposal.getProposedPercent().compareTo(next) == 0) {
                        accept(proposal, requireRule(ruleId), actor);
                    } else {
                        decide(proposal, CommissionRateProposal.Status.REJECTED, CommissionRuleHistory.EventType.BID_REJECTED, actor);
                    }
                });
        seat = requireSeat(ruleId, vendorClientId);
        if (seat.getCommissionPercent() != null && seat.getCommissionPercent().compareTo(next) == 0) {
            return get(ruleId);
        }
        assertPercentFree(ruleId, vendorClientId, next);
        seat.setCommissionPercent(next);
        vendorRepository.save(seat);
        record(ruleId, vendorClientId, CommissionRuleHistory.EventType.RATE_ENTERED, next, actor);
        return get(ruleId);
    }

    @Transactional
    public void delete(UUID ruleId) {
        CommissionRule rule = requireRule(ruleId);
        proposalRepository.deleteByRuleId(rule.getId());
        ruleRepository.delete(rule);
    }

    @Transactional
    public RuleDetail setActive(UUID ruleId, boolean active, String actor) {
        CommissionRule rule = requireRule(ruleId);
        if (rule.isActive() != active) {
            rule.setActive(active);
            ruleRepository.save(rule);
            record(rule.getId(), null,
                    active ? CommissionRuleHistory.EventType.RULE_ACTIVATED : CommissionRuleHistory.EventType.RULE_DEACTIVATED,
                    null, actor);
        }
        return get(rule.getId());
    }

    @Transactional
    public VendorRuleView propose(UUID ruleId, UUID vendorClientId, BigDecimal proposedPercent, String actor) {
        CommissionRule rule = requireRule(ruleId);
        if (!rule.isActive()) {
            throw new BadRequestException("This rule is not active");
        }
        CommissionRuleVendor seat = requireSeat(ruleId, vendorClientId);
        BigDecimal next = percent(proposedPercent);
        if (seat.getCommissionPercent() != null && seat.getCommissionPercent().compareTo(next) == 0) {
            throw new BadRequestException("That is already your commission");
        }
        if (proposalRepository.findByRuleIdAndVendorClientIdAndStatus(
                ruleId, vendorClientId, CommissionRateProposal.Status.PENDING).isPresent()) {
            throw new BadRequestException("A commission is already waiting for Paytrix");
        }
        CommissionRateProposal proposal = new CommissionRateProposal();
        proposal.setRuleId(ruleId);
        proposal.setVendorClientId(vendorClientId);
        proposal.setProposedPercent(next);
        proposal.setStatus(CommissionRateProposal.Status.PENDING);
        proposalRepository.save(proposal);
        record(ruleId, vendorClientId, CommissionRuleHistory.EventType.BID_SUBMITTED, next, actor);
        return listForVendor(vendorClientId).stream()
                .filter(view -> view.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
    }

    @Transactional
    public RuleDetail accept(UUID proposalId, String actor) {
        CommissionRateProposal proposal = pending(proposalId);
        CommissionRule rule = requireRule(proposal.getRuleId());
        accept(proposal, rule, actor);
        return get(rule.getId());
    }

    @Transactional
    public RuleDetail reject(UUID proposalId, String actor) {
        CommissionRateProposal proposal = pending(proposalId);
        decide(proposal, CommissionRateProposal.Status.REJECTED, CommissionRuleHistory.EventType.BID_REJECTED, actor);
        return get(proposal.getRuleId());
    }

    /** The vendor's current rate, present only while the rule and that vendor are both active. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> livePercent(UUID ruleId, UUID vendorClientId) {
        if (ruleId == null || vendorClientId == null) {
            return Optional.empty();
        }
        CommissionRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.isActive()) {
            return Optional.empty();
        }
        return vendorRepository.findByRuleIdAndVendorClientId(ruleId, vendorClientId)
                .filter(CommissionRuleVendor::isActive)
                .map(CommissionRuleVendor::getCommissionPercent);
    }

    @Transactional(readOnly = true)
    public List<AssignmentOption> liveRates(String categoryId, String providerBillerId) {
        if (categoryId == null || providerBillerId == null) {
            return List.of();
        }
        List<CommissionRule> rules = ruleRepository
                .findByCategoryIdAndProviderBillerId(categoryId, providerBillerId)
                .stream()
                .filter(CommissionRule::isActive)
                .toList();
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<UUID, CommissionRule> byId = rules.stream().collect(Collectors.toMap(CommissionRule::getId, Function.identity()));
        List<AssignmentOption> options = new ArrayList<>();
        for (CommissionRuleVendor seat : vendorRepository.findByRuleIdIn(byId.keySet())) {
            if (!seat.isActive() || seat.getCommissionPercent() == null) {
                continue;
            }
            CommissionRule rule = byId.get(seat.getRuleId());
            options.add(new AssignmentOption(
                    rule.getId(), seat.getVendorClientId(), seat.getCommissionPercent(), seat.getCreatedAt(), rule.getRange()));
        }
        return options;
    }

    private void accept(CommissionRateProposal proposal, CommissionRule rule, String actor) {
        assertPercentFree(rule.getId(), proposal.getVendorClientId(), proposal.getProposedPercent());
        CommissionRuleVendor seat = requireSeat(rule.getId(), proposal.getVendorClientId());
        seat.setCommissionPercent(proposal.getProposedPercent());
        vendorRepository.save(seat);
        decide(proposal, CommissionRateProposal.Status.ACCEPTED, CommissionRuleHistory.EventType.BID_ACCEPTED, actor);
    }

    private void decide(
            CommissionRateProposal proposal, CommissionRateProposal.Status status,
            CommissionRuleHistory.EventType event, String actor) {
        proposal.setStatus(status);
        proposal.setDecidedAt(Instant.now());
        String signature = sign(actor);
        proposal.setDecidedBy(signature);
        proposalRepository.save(proposal);
        record(proposal.getRuleId(), proposal.getVendorClientId(), event, proposal.getProposedPercent(), signature);
    }

    private CommissionRateProposal pending(UUID proposalId) {
        CommissionRateProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Commission bid not found"));
        if (proposal.getStatus() != CommissionRateProposal.Status.PENDING) {
            throw new BadRequestException("This commission has already been decided");
        }
        return proposal;
    }

    private List<PreparedVendor> prepareVendors(List<NewVendor> vendors) {
        List<PreparedVendor> prepared = new ArrayList<>();
        Set<UUID> seenVendors = new HashSet<>();
        List<BigDecimal> seenPercents = new ArrayList<>();
        for (NewVendor request : vendors) {
            if (!seenVendors.add(request.vendorClientId())) {
                throw new BadRequestException("Select each vendor once");
            }
            Client vendor = requireSelectableVendor(request.vendorClientId());
            BigDecimal percent = request.commissionPercent() == null ? null : percent(request.commissionPercent());
            if (percent != null && seenPercents.stream().anyMatch(existing -> existing.compareTo(percent) == 0)) {
                throw new BadRequestException("Two vendors cannot use " + percent.toPlainString() + "% on the same rule");
            }
            if (percent != null) {
                seenPercents.add(percent);
            }
            prepared.add(new PreparedVendor(vendor, percent));
        }
        return prepared;
    }

    private CommissionAmountRange requireFreeRange(UUID rangeId) {
        CommissionAmountRange range = rangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Amount range not found"));
        ruleRepository.findByRange_Id(rangeId).ifPresent(existing -> {
            throw new BadRequestException("This amount range already has a rule");
        });
        return range;
    }

    private void assertPercentFree(UUID ruleId, UUID vendorClientId, BigDecimal percent) {
        for (CommissionRuleVendor seat : vendorRepository.findByRuleId(ruleId)) {
            if (seat.getVendorClientId().equals(vendorClientId) || seat.getCommissionPercent() == null) {
                continue;
            }
            if (seat.getCommissionPercent().compareTo(percent) == 0) {
                throw new BadRequestException(vendorName(seat.getVendorClientId()) + " already uses "
                        + seat.getCommissionPercent().toPlainString() + "% on this rule");
            }
        }
    }

    private Client requireSelectableVendor(UUID vendorClientId) {
        Client vendor = clientService.getById(vendorClientId);
        if (vendor.getClientType() != Client.ClientType.VENDOR) {
            throw new BadRequestException("Rules can only include a vendor");
        }
        if (vendor.getStatus() != Client.ClientStatus.ACTIVE && vendor.getStatus() != Client.ClientStatus.PENDING) {
            throw new BadRequestException(vendor.getClientName() + " is not available for a rule");
        }
        return vendor;
    }

    private CommissionRule requireRule(UUID ruleId) {
        return ruleRepository.findById(ruleId).orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
    }

    private CommissionRuleVendor requireSeat(UUID ruleId, UUID vendorClientId) {
        return vendorRepository.findByRuleIdAndVendorClientId(ruleId, vendorClientId)
                .orElseThrow(() -> new ResourceNotFoundException("This vendor is not on the rule"));
    }

    /** A phone number is stored as that user's code. A blank actor stays the Paytrix label. */
    private String sign(String actor) {
        if (actor == null || actor.isBlank() || "Paytrix".equals(actor)) {
            return "Paytrix";
        }
        return appUserRepository.findByPhoneNumber(actor).map(user -> user.getUserCode()).orElse(actor);
    }

    private void record(
            UUID ruleId, UUID vendorClientId, CommissionRuleHistory.EventType event, BigDecimal percent, String actor) {
        CommissionRuleHistory row = new CommissionRuleHistory();
        row.setRuleId(ruleId);
        row.setVendorClientId(vendorClientId);
        row.setEventType(event);
        row.setCommissionPercent(percent);
        row.setActor(sign(actor));
        historyRepository.save(row);
    }

    private List<CommissionRateProposal> pendingFor(List<UUID> ruleIds) {
        if (ruleIds.isEmpty()) {
            return List.of();
        }
        return proposalRepository.findByRuleIdInAndStatus(ruleIds, CommissionRateProposal.Status.PENDING);
    }

    private List<RuleView> toViews(List<CommissionRule> rules) {
        List<UUID> ids = rules.stream().map(CommissionRule::getId).toList();
        Map<UUID, List<CommissionRuleVendor>> seats = ids.isEmpty()
                ? Map.of()
                : vendorRepository.findByRuleIdIn(ids).stream().collect(Collectors.groupingBy(CommissionRuleVendor::getRuleId));
        Map<String, CommissionRateProposal> pending = pendingFor(ids).stream()
                .collect(Collectors.toMap(proposal -> proposal.getRuleId() + ":" + proposal.getVendorClientId(), Function.identity(), (a, b) -> a));
        List<RuleView> views = new ArrayList<>();
        for (CommissionRule rule : rules) {
            List<VendorRateView> vendors = new ArrayList<>();
            BigDecimal highest = null;
            for (CommissionRuleVendor seat : seats.getOrDefault(rule.getId(), List.of())) {
                CommissionRateProposal proposal = pending.get(rule.getId() + ":" + seat.getVendorClientId());
                vendors.add(new VendorRateView(
                        seat.getId(),
                        seat.getVendorClientId(),
                        vendorName(seat.getVendorClientId()),
                        seat.getCommissionPercent(),
                        seat.isActive(),
                        proposal == null ? null : proposal.getId(),
                        proposal == null ? null : proposal.getProposedPercent()));
                if (seat.isActive() && seat.getCommissionPercent() != null
                        && (highest == null || seat.getCommissionPercent().compareTo(highest) > 0)) {
                    highest = seat.getCommissionPercent();
                }
            }
            vendors.sort(Comparator.comparing(VendorRateView::commissionPercent, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(VendorRateView::vendorName));
            views.add(new RuleView(
                    rule.getId(),
                    rule.getCategoryId(),
                    rule.getCategoryName(),
                    rule.getProviderBillerId(),
                    rule.getProviderName(),
                    rangeView(rule.getRange()),
                    vendors,
                    highest,
                    rule.isActive(),
                    rule.getCreatedAt()));
        }
        return views;
    }

    private List<HistoryView> history(UUID ruleId) {
        List<HistoryView> views = new ArrayList<>();
        for (CommissionRuleHistory row : historyRepository.findByRuleIdOrderByCreatedAtDesc(ruleId)) {
            views.add(new HistoryView(
                    row.getId(),
                    row.getVendorClientId(),
                    row.getVendorClientId() == null ? null : vendorName(row.getVendorClientId()),
                    row.getEventType().name(),
                    row.getCommissionPercent(),
                    row.getActor(),
                    row.getCreatedAt()));
        }
        return views;
    }

    private static RangeView rangeView(CommissionAmountRange range) {
        return new RangeView(range.getId(), range.getMinAmount(), range.getMaxAmount());
    }

    private String vendorName(UUID vendorClientId) {
        return clientService.getById(vendorClientId).getClientName();
    }

    public static BigDecimal percent(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("Enter a commission percent");
        }
        if (value.stripTrailingZeros().scale() > 3) {
            throw new BadRequestException("Commission percent can have at most 3 decimal places");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Commission percent must be greater than 0 and at most 100");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private record PreparedVendor(Client client, BigDecimal percent) {
    }

    public record NewVendor(UUID vendorClientId, BigDecimal commissionPercent) {
    }

    public record NewRule(
            String categoryId,
            String categoryName,
            String providerBillerId,
            String providerName,
            UUID rangeId,
            List<NewVendor> vendors) {
    }

    public record RangeView(UUID id, BigDecimal minAmount, BigDecimal maxAmount) {
    }

    public record VendorRateView(
            UUID id,
            UUID vendorClientId,
            String vendorName,
            BigDecimal commissionPercent,
            boolean active,
            UUID pendingProposalId,
            BigDecimal pendingCommissionPercent) {
    }

    public record RuleView(
            UUID id,
            String categoryId,
            String categoryName,
            String providerBillerId,
            String providerName,
            RangeView range,
            List<VendorRateView> vendors,
            BigDecimal highestCommissionPercent,
            boolean active,
            Instant createdAt) {
    }

    public record VendorRuleView(
            UUID id,
            String categoryId,
            String categoryName,
            String providerBillerId,
            String providerName,
            RangeView range,
            BigDecimal commissionPercent,
            boolean rateActive,
            UUID pendingProposalId,
            BigDecimal pendingCommissionPercent,
            Instant createdAt) {
    }

    public record HistoryView(
            UUID id,
            UUID vendorClientId,
            String vendorName,
            String eventType,
            BigDecimal commissionPercent,
            String actor,
            Instant createdAt) {
    }

    public record RuleDetail(RuleView rule, List<HistoryView> history) {
    }

    public record AssignmentOption(
            UUID ruleId,
            UUID vendorClientId,
            BigDecimal commissionPercent,
            Instant createdAt,
            CommissionAmountRange range) {
    }
}
