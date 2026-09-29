import { useCallback, useEffect, useState } from "react";
import {
  buildApprove,
  buildExecute,
  buildInitGovernance,
  buildPropose,
  confirmApprove,
  confirmExecute,
  confirmInitGovernance,
  confirmPropose,
  fetchGovernance,
  type GovernanceView,
  type ProposalInput,
  type ProposalRow,
} from "../services/governanceService";
import { stepMessage, useWalletStep } from "./useWalletStep";

export type GovernanceState = {
  loading: boolean;
  busy: boolean;
  error: string | null;
  view: GovernanceView | null;
  walletReady: boolean;
  walletPubkey: string | null;
  isSigner: boolean;
  proposal: (id: number) => ProposalRow | null;
  init: (signers: string[], threshold: number) => Promise<string>;
  propose: (input: ProposalInput) => Promise<string>;
  approve: (id: number) => Promise<string>;
  execute: (id: number) => Promise<string>;
};

/**
 * Owner multisig: signer set, proposals, and the wallet-signed init / propose / approve / execute.
 */
export function useGovernance(): GovernanceState {
  const step = useWalletStep();
  const walletPubkey = step.walletPubkey;
  const [view, setView] = useState<GovernanceView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!walletPubkey) {
      setView(null);
      return;
    }
    setLoading(true);
    try {
      setView(await fetchGovernance(walletPubkey));
      setError(null);
    } catch (err) {
      setView(null);
      setError(stepMessage(err, "Could not load governance"));
    } finally {
      setLoading(false);
    }
  }, [walletPubkey]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const proposal = useCallback(
    (id: number) => view?.proposals.find((row) => row.proposal.id === id) ?? null,
    [view],
  );

  const init = useCallback(
    async (signers: string[], threshold: number) => {
      const sig = await step.run(
        (w) => buildInitGovernance({ walletPubkey: w, signers, threshold }),
        (w, txSignature) => confirmInitGovernance({ walletPubkey: w, signers, threshold, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  const propose = useCallback(
    async (input: ProposalInput) => {
      let proposalId = -1;
      const sig = await step.run(
        async (w) => {
          const built = await buildPropose({ walletPubkey: w, ...input });
          proposalId = built.proposalId ?? -1;
          return built;
        },
        (w, txSignature) => confirmPropose({ walletPubkey: w, ...input, proposalId, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  const approve = useCallback(
    async (id: number) => {
      const sig = await step.run(
        (w) => buildApprove(id, { walletPubkey: w }),
        (w, txSignature) => confirmApprove(id, { walletPubkey: w, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  const execute = useCallback(
    async (id: number) => {
      const sig = await step.run(
        (w) => buildExecute(id, { walletPubkey: w }),
        (w, txSignature) => confirmExecute(id, { walletPubkey: w, txSignature }),
      );
      await refresh();
      return sig;
    },
    [step, refresh],
  );

  return {
    loading,
    busy: step.busy,
    error,
    view,
    walletReady: Boolean(walletPubkey),
    walletPubkey,
    isSigner: Boolean(walletPubkey && view?.signers.includes(walletPubkey)),
    proposal,
    init,
    propose,
    approve,
    execute,
  };
}
