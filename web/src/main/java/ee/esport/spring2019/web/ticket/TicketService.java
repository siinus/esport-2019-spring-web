package ee.esport.spring2019.web.ticket;

import ee.esport.spring2019.web.email.EmailService;
import ee.esport.spring2019.web.ticket.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

    @Resource
    private final TicketRepository ticketRepository;

    @Resource
    private final EmailService emailService;

    public List<TicketType> getAllTypes() {
        return ticketRepository.getAllTypes();
    }

    public TicketType getType(int typeId) {
        return ticketRepository.getType(typeId);
    }

    public TicketOffering getVisibleOffering(int id) {
        return getVisibleOfferings().stream()
                                    .filter(it -> it.getId() == id)
                                    .findAny()
                                    .orElseThrow(() -> new NoSuchElementException("Ticket offering not visible or" +
                                                                                  "does not exist"));
    }

    // No more than one per type, active or next to be active
    public List<TicketOffering> getVisibleOfferings() {
        return ticketRepository.getAllOfferings()
                               .stream()
                               .collect(Collectors.groupingBy(TicketOffering::getTypeId))
                               .values()
                               .stream()
                               .map(this::findVisibleOffering)
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList());
    }

    private TicketOffering findVisibleOffering(List<TicketOffering> offerings) {
        Optional<TicketOffering> activeOffering = offerings.stream()
                                                           .filter(this::isActive)
                                                           .findAny();
        if (activeOffering.isPresent()) {
            return activeOffering.get();
        }
        Optional<TicketOffering> nextActiveOffering =
                offerings.stream()
                         .filter(offering -> !isExpired(offering))
                         .min(Comparator.comparing(TicketOffering::getAvailableFrom));
        return nextActiveOffering.orElse(null);
    }

    private boolean isActive(TicketOffering offering) {
        return !isNotYetActive(offering) && !isExpired(offering);
    }

    private boolean isNotYetActive(TicketOffering offering) {
        return offering.getAvailableFrom().isAfter(OffsetDateTime.now());
    }

    private boolean isExpired(TicketOffering offering) {
        return !offering.getAvailableUntil().isAfter(OffsetDateTime.now());
    }

    @Transactional
    public Ticket createTicket(TicketCreation creation) {
        TicketOffering offering = ticketRepository.getOffering(creation.getOfferingId());
        if (!isActive(offering)) {
            throw new IllegalArgumentException("Ticket offering not active");
        }
        if (!offering.getAvailableOnline()) {
            throw new IllegalArgumentException("Ticket offering not available online");
        }
        Ticket.Status status = offering.getAmountRemaining() > 0 ?
                               Ticket.Status.AWAITING_PAYMENT :
                               Ticket.Status.IN_WAITING_LIST;
        TicketCandidate candidate = TicketCandidate.builder()
                                                   .offeringId(creation.getOfferingId())
                                                   .ownerId(creation.getOwnerId())
                                                   .seat(creation.getSeat())
                                                   .status(status)
                                                   .name(creation.getName())
                                                   .build();
        Ticket ticket = ticketRepository.createTicket(candidate);
        //sendTicketCreationEmail(ticket);
        return ticket;
    }

    @SneakyThrows //FIXME: remove
    private void sendTicketCreationEmail(Ticket ticket) {
        if(ticket.getStatus() == Ticket.Status.AWAITING_PAYMENT) {
            emailService.sendTicketReservation(ticket).get();
        } else {
            emailService.sendTicketWaiting(ticket).get();
        }
    }

    public Ticket getTicket(int ticketId) {
        return ticketRepository.getTicket(ticketId);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.getAllTickets();
    }

    public void cancelTicket(Ticket ticket) {
        throw new NotImplementedException("Ticket cancellation not yet implemented");
    }

    public void confirmTicketPaid(Ticket ticket, String referer) {
        throw new NotImplementedException("Ticket payment confirmation not yet implemented");
    }

    public void deleteMember(int ticketId, int memberId) {
        ticketRepository.deleteMember(ticketId, memberId);
    }

}
