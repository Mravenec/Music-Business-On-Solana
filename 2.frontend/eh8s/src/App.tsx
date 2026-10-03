import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { SolanaProviders } from "./components/SolanaProviders";
import { AppShell } from "./components/AppShell";
import { SessionProvider } from "./hooks/useSession";
import { RolesProvider } from "./hooks/useRoles";
import { HomePage } from "./pages/HomePage";
import { ApplyPage } from "./pages/ApplyPage";
import { MorePage } from "./pages/MorePage";
import { AcademyPage } from "./pages/AcademyPage";
import { AcademyPlanPage } from "./pages/AcademyPlanPage";
import { AcademyEnrollPage } from "./pages/AcademyEnrollPage";
import { AcademyEvaluatePage } from "./pages/AcademyEvaluatePage";
import { AcademyCoursesPage } from "./pages/AcademyCoursesPage";
import { AcademyCoursePage } from "./pages/AcademyCoursePage";
import { AcademyLessonPage } from "./pages/AcademyLessonPage";
import { OwnerCoursesPage } from "./pages/OwnerCoursesPage";
import { OwnerCourseFormPage } from "./pages/OwnerCourseFormPage";
import { OwnerCoursePage } from "./pages/OwnerCoursePage";
import { OwnerSectionPage } from "./pages/OwnerSectionPage";
import { OwnerLessonFormPage } from "./pages/OwnerLessonFormPage";
import { OwnerCourseReviewsPage } from "./pages/OwnerCourseReviewsPage";
import { OwnerCourseReviewPage } from "./pages/OwnerCourseReviewPage";
import { TeachPage } from "./pages/TeachPage";
import { OwnerCourseAccessPage } from "./pages/OwnerCourseAccessPage";
import { OwnerCourseGrantPage } from "./pages/OwnerCourseGrantPage";
import { BandsPage } from "./pages/BandsPage";
import { BandNewPage } from "./pages/BandNewPage";
import { BandDetailPage } from "./pages/BandDetailPage";
import { BandAddMemberPage } from "./pages/BandAddMemberPage";
import { BandRehearsalNewPage } from "./pages/BandRehearsalNewPage";
import { BandCheckInPage } from "./pages/BandCheckInPage";
import { BandRatePage } from "./pages/BandRatePage";
import { BandCyclePage } from "./pages/BandCyclePage";
import { BandVaultActivatePage } from "./pages/BandVaultActivatePage";
import { BandVaultSyncPage } from "./pages/BandVaultSyncPage";
import { StageMapPage } from "./pages/StageMapPage";
import { StageVenuePage } from "./pages/StageVenuePage";
import { StageConcertsPage } from "./pages/StageConcertsPage";
import { StageConcertSetPage } from "./pages/StageConcertSetPage";
import { StageSlotRequestPage } from "./pages/StageSlotRequestPage";
import { StageVenueDatesPage } from "./pages/StageVenueDatesPage";
import { StageBookPage } from "./pages/StageBookPage";
import { StageSettlePage } from "./pages/StageSettlePage";
import { StageClaimPage } from "./pages/StageClaimPage";
import { StageVenueNewPage } from "./pages/StageVenueNewPage";
import { CatalogPage } from "./pages/CatalogPage";
import { CatalogNewPage } from "./pages/CatalogNewPage";
import { CatalogTrackPage } from "./pages/CatalogTrackPage";
import { CatalogSongPoolPage } from "./pages/CatalogSongPoolPage";
import { CatalogSyncPage } from "./pages/CatalogSyncPage";
import { CatalogSyncNewPage } from "./pages/CatalogSyncNewPage";
import { CatalogSyncDealPage } from "./pages/CatalogSyncDealPage";
import { SongCreditsPage } from "./pages/SongCreditsPage";
import { CatalogDepositPage } from "./pages/CatalogDepositPage";
import { CatalogReachPage } from "./pages/CatalogReachPage";
import { OwnerPage } from "./pages/OwnerPage";
import { OwnerRolesPage } from "./pages/OwnerRolesPage";
import { OwnerRolesGrantedPage } from "./pages/OwnerRolesGrantedPage";
import { OwnerDecisionsPage } from "./pages/OwnerDecisionsPage";
import { OwnerDecisionsAnsweredPage } from "./pages/OwnerDecisionsAnsweredPage";
import { OwnerSlackPage } from "./pages/OwnerSlackPage";
import { OwnerTreasuryPage } from "./pages/OwnerTreasuryPage";
import { OwnerTreasuryActivityPage } from "./pages/OwnerTreasuryActivityPage";
import { OwnerDigestPage } from "./pages/OwnerDigestPage";
import { OwnerAgentsPage } from "./pages/OwnerAgentsPage";
import { OwnerAgentAuthorizePage } from "./pages/OwnerAgentAuthorizePage";
import { GovernancePage } from "./pages/GovernancePage";
import { GovernanceSetupPage } from "./pages/GovernanceSetupPage";
import { GovernanceNewPage } from "./pages/GovernanceNewPage";
import { GovernanceProposePage } from "./pages/GovernanceProposePage";
import { GovernanceProposalPage } from "./pages/GovernanceProposalPage";
import { AgentLevelsPage } from "./pages/AgentLevelsPage";
import { AgentLevelSetPage } from "./pages/AgentLevelSetPage";
import { VenueOnchainPage } from "./pages/VenueOnchainPage";
import { VenueBookingEscrowPage } from "./pages/VenueBookingEscrowPage";
import { StageQueuePage } from "./pages/StageQueuePage";
import { StageVenueApprovePage } from "./pages/StageVenueApprovePage";
import { StageBookingConfirmPage } from "./pages/StageBookingConfirmPage";
import { VaultQueuePage } from "./pages/VaultQueuePage";
import { VaultSettlePage } from "./pages/VaultSettlePage";
import { DevnetPayPage } from "./pages/DevnetPayPage";
import { PaymentNewPage } from "./pages/PaymentNewPage";
import { PaymentClaimPage } from "./pages/PaymentClaimPage";
import { AgentEventsPage } from "./pages/AgentEventsPage";
import { HealthPage } from "./pages/HealthPage";
import { SolanaPage } from "./pages/SolanaPage";
import { AnchorDocsPage } from "./pages/AnchorDocsPage";
import { NexusPage } from "./pages/NexusPage";
import { NexusRequestPage } from "./pages/NexusRequestPage";
import { NexusEvaluationPage } from "./pages/NexusEvaluationPage";
import { BandMatchPage } from "./pages/BandMatchPage";
import { BandToursPage } from "./pages/BandToursPage";
import { BandTourNewPage } from "./pages/BandTourNewPage";
import { BandTourPage } from "./pages/BandTourPage";
import { MyEarningsPage, StudioLedgerPage } from "./pages/StudioLedgerPage";

export function App() {
  return (
    <SolanaProviders>
      <SessionProvider>
        <RolesProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<AppShell />}>
                <Route path="/" element={<HomePage />} />
                <Route path="/apply" element={<ApplyPage />} />
                <Route path="/more" element={<MorePage />} />
                <Route path="/health" element={<Navigate to="/status" replace />} />
                <Route path="/status" element={<HealthPage />} />
                <Route path="/academy" element={<AcademyPage />} />
                <Route path="/studio-ledger" element={<StudioLedgerPage />} />
                <Route path="/studio-ledger/mine" element={<MyEarningsPage />} />
                <Route path="/academy/enroll" element={<AcademyEnrollPage />} />
                <Route path="/academy/evaluate" element={<AcademyEvaluatePage />} />
                <Route path="/academy/plans/:planId" element={<AcademyPlanPage />} />
                <Route path="/academy/nexus" element={<NexusPage />} />
                <Route path="/academy/nexus/new" element={<NexusRequestPage />} />
                <Route path="/academy/nexus/:evaluationId" element={<NexusEvaluationPage />} />
                <Route path="/academy/courses" element={<AcademyCoursesPage />} />
                <Route path="/academy/courses/:courseId" element={<AcademyCoursePage />} />
                <Route path="/academy/lessons/:lessonId" element={<AcademyLessonPage />} />
                <Route path="/academy/teach" element={<TeachPage />} />
                <Route path="/academy/teach/courses" element={<OwnerCoursesPage />} />
                <Route path="/academy/teach/courses/new" element={<OwnerCourseFormPage />} />
                <Route path="/academy/teach/courses/:courseId" element={<OwnerCoursePage />} />
                <Route path="/academy/teach/courses/:courseId/edit" element={<OwnerCourseFormPage />} />
                <Route path="/academy/teach/courses/:courseId/sections/:sectionId" element={<OwnerSectionPage />} />
                <Route
                  path="/academy/teach/courses/:courseId/sections/:sectionId/lessons/new"
                  element={<OwnerLessonFormPage />}
                />
                <Route path="/academy/teach/courses/:courseId/lessons/:lessonId" element={<OwnerLessonFormPage />} />
                <Route path="/bands" element={<BandsPage />} />
                <Route path="/bands/new" element={<BandNewPage />} />
                <Route path="/bands/:bandId" element={<BandDetailPage />} />
                <Route path="/bands/:bandId/members/add" element={<BandAddMemberPage />} />
                <Route path="/bands/:bandId/rehearsals/new" element={<BandRehearsalNewPage />} />
                <Route path="/bands/:bandId/vault/activate" element={<BandVaultActivatePage />} />
                <Route path="/bands/:bandId/vault/sync" element={<BandVaultSyncPage />} />
                <Route
                  path="/bands/:bandId/rehearsals/:sessionId"
                  element={<BandCheckInPage />}
                />
                <Route
                  path="/bands/:bandId/rehearsals/:sessionId/rate"
                  element={<BandRatePage />}
                />
                <Route path="/bands/:bandId/cycle" element={<BandCyclePage />} />
                <Route path="/bands/:bandId/match" element={<BandMatchPage />} />
                <Route path="/bands/:bandId/tours" element={<BandToursPage />} />
                <Route path="/bands/:bandId/tours/new" element={<BandTourNewPage />} />
                <Route path="/bands/:bandId/tours/:planId" element={<BandTourPage />} />
                <Route path="/stage-map" element={<StageMapPage />} />
                <Route path="/stage-map/book" element={<StageBookPage />} />
                <Route path="/stage-map/settle" element={<StageSettlePage />} />
                <Route path="/stage-map/claim" element={<StageClaimPage />} />
                <Route path="/stage-map/venues/new" element={<StageVenueNewPage />} />
                <Route path="/stage-map/venues/:venueId" element={<StageVenuePage />} />
                <Route path="/stage-map/venues/:venueId/request" element={<StageSlotRequestPage />} />
                <Route path="/stage-map/venues/:venueId/dates" element={<StageVenueDatesPage />} />
                <Route path="/stage-map/concerts" element={<StageConcertsPage />} />
                <Route path="/stage-map/concerts/:concertId/set" element={<StageConcertSetPage />} />
                <Route path="/stage-map/venues/:venueId/onchain" element={<VenueOnchainPage />} />
                <Route
                  path="/stage-map/venues/:venueId/bookings/:bookingId"
                  element={<VenueBookingEscrowPage />}
                />
                <Route path="/catalog" element={<CatalogPage />} />
                <Route path="/catalog/new" element={<CatalogNewPage />} />
                <Route path="/catalog/reach" element={<CatalogReachPage />} />
                <Route path="/catalog/credits" element={<SongCreditsPage />} />
                <Route path="/catalog/tracks/:trackId" element={<CatalogTrackPage />} />
                <Route path="/catalog/tracks/:trackId/pool" element={<CatalogSongPoolPage />} />
                <Route path="/catalog/tracks/:trackId/sync" element={<CatalogSyncPage />} />
                <Route path="/catalog/tracks/:trackId/sync/new" element={<CatalogSyncNewPage />} />
                <Route
                  path="/catalog/tracks/:trackId/sync/:dealId"
                  element={<CatalogSyncDealPage />}
                />
                <Route
                  path="/catalog/tracks/:trackId/deposit"
                  element={<CatalogDepositPage />}
                />
                <Route path="/owner" element={<OwnerPage />} />
                <Route path="/owner/courses" element={<OwnerCoursesPage />} />
                <Route path="/owner/courses/new" element={<OwnerCourseFormPage />} />
                <Route path="/owner/courses/review" element={<OwnerCourseReviewsPage />} />
                <Route path="/owner/courses/review/:courseId" element={<OwnerCourseReviewPage />} />
                <Route path="/owner/courses/:courseId" element={<OwnerCoursePage />} />
                <Route path="/owner/courses/:courseId/edit" element={<OwnerCourseFormPage />} />
                <Route path="/owner/courses/:courseId/sections/:sectionId" element={<OwnerSectionPage />} />
                <Route path="/owner/courses/:courseId/sections/:sectionId/lessons/new" element={<OwnerLessonFormPage />} />
                <Route path="/owner/courses/:courseId/lessons/:lessonId" element={<OwnerLessonFormPage />} />
                <Route path="/owner/course-access" element={<OwnerCourseAccessPage />} />
                <Route path="/owner/course-access/new" element={<OwnerCourseGrantPage />} />
                <Route path="/owner/roles" element={<OwnerRolesPage />} />
                <Route path="/owner/roles/granted" element={<OwnerRolesGrantedPage />} />
                <Route path="/owner/decisions" element={<OwnerDecisionsPage />} />
                <Route path="/owner/decisions/answered" element={<OwnerDecisionsAnsweredPage />} />
                <Route path="/owner/slack" element={<OwnerSlackPage />} />
                <Route path="/owner/treasury" element={<OwnerTreasuryPage />} />
                <Route path="/owner/treasury/activity" element={<OwnerTreasuryActivityPage />} />
                <Route path="/owner/digest" element={<OwnerDigestPage />} />
                <Route path="/owner/agents" element={<OwnerAgentsPage />} />
                <Route path="/owner/agents/:code" element={<OwnerAgentAuthorizePage />} />
                <Route path="/owner/governance" element={<GovernancePage />} />
                <Route path="/owner/governance/setup" element={<GovernanceSetupPage />} />
                <Route path="/owner/governance/new" element={<GovernanceNewPage />} />
                <Route path="/owner/governance/new/:kind" element={<GovernanceProposePage />} />
                <Route path="/owner/governance/proposals/:id" element={<GovernanceProposalPage />} />
                <Route path="/agents/levels" element={<AgentLevelsPage />} />
                <Route
                  path="/agents/levels/:musicianProfileId"
                  element={<AgentLevelSetPage />}
                />
                <Route path="/agents/stage" element={<StageQueuePage />} />
                <Route path="/agents/stage/venues/:venueId" element={<StageVenueApprovePage />} />
                <Route path="/agents/stage/bookings/:bookingId" element={<StageBookingConfirmPage />} />
                <Route path="/agents/vault" element={<VaultQueuePage />} />
                <Route path="/agents/vault/bookings/:bookingId" element={<VaultSettlePage />} />
                <Route path="/devnet-pay" element={<DevnetPayPage />} />
                <Route path="/devnet-pay/new" element={<PaymentNewPage />} />
                <Route path="/devnet-pay/claim" element={<PaymentClaimPage />} />
                <Route path="/agent-events" element={<AgentEventsPage />} />
                <Route path="/solana" element={<SolanaPage />} />
                <Route path="/anchor" element={<AnchorDocsPage />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </RolesProvider>
      </SessionProvider>
    </SolanaProviders>
  );
}
