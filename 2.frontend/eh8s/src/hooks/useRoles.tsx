import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useSession } from "./useSession";
import {
  APPLYABLE_ROLES,
  applyForRole,
  approveRoleApplication,
  fetchRoleApplications,
  fetchRoleMemberships,
  rejectRoleApplication,
  grantRole,
  revokeRoleApplication,
  type AccountRole,
  type RoleApplication,
} from "../services/roleApplicationService";

const WORKSPACE_KEY = "eh8s.activeWorkspaceRole";

type RolesState = {
  memberships: AccountRole[];
  myApplications: RoleApplication[];
  pendingQueue: RoleApplication[];
  grantedQueue: RoleApplication[];
  activeWorkspace: string | null;
  setActiveWorkspace: (role: string) => void;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  apply: (role: string) => Promise<void>;
  approve: (id: number, reason?: string) => Promise<void>;
  reject: (id: number, reason?: string) => Promise<void>;
  revoke: (id: number, reason?: string) => Promise<void>;
  grant: (walletPubkey: string, role: string) => Promise<void>;
  applyableRoles: readonly string[];
};

const RolesContext = createContext<RolesState>({
  memberships: [],
  myApplications: [],
  pendingQueue: [],
  grantedQueue: [],
  activeWorkspace: null,
  setActiveWorkspace: () => undefined,
  loading: false,
  error: null,
  refresh: async () => undefined,
  apply: async () => undefined,
  approve: async () => undefined,
  reject: async () => undefined,
  revoke: async () => undefined,
  grant: async () => undefined,
  applyableRoles: APPLYABLE_ROLES,
});

/**
 * Loads approved roles and applications; tracks active workspace for nav.
 */
export function RolesProvider({ children }: { children: ReactNode }) {
  const { session } = useSession();
  const [memberships, setMemberships] = useState<AccountRole[]>([]);
  const [myApplications, setMyApplications] = useState<RoleApplication[]>([]);
  const [pendingQueue, setPendingQueue] = useState<RoleApplication[]>([]);
  const [grantedQueue, setGrantedQueue] = useState<RoleApplication[]>([]);
  const [activeWorkspace, setActiveWorkspaceState] = useState<string | null>(
    () => localStorage.getItem(WORKSPACE_KEY)
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const wallet = session?.account?.walletPubkey ?? null;
  const isOwner = Boolean(
    session?.platformOwner ||
      session?.studioAdmin ||
      session?.account?.role?.toLowerCase() === "owner"
  );

  const setActiveWorkspace = useCallback((role: string) => {
    const next = role.toLowerCase();
    localStorage.setItem(WORKSPACE_KEY, next);
    setActiveWorkspaceState(next);
  }, []);

  const refresh = useCallback(async () => {
    if (!wallet) {
      setMemberships([]);
      setMyApplications([]);
      setPendingQueue([]);
      setGrantedQueue([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (isOwner) {
        setMemberships([]);
        setMyApplications([]);
        const pending = await fetchRoleApplications("pending");
        const granted = await fetchRoleApplications("approved");
        setPendingQueue(pending);
        setGrantedQueue(granted);
      } else {
        const [roles, apps] = await Promise.all([
          fetchRoleMemberships(wallet),
          fetchRoleApplications(undefined, wallet),
        ]);
        setMemberships(roles);
        setMyApplications(apps);
        setPendingQueue([]);
        setGrantedQueue([]);
        const roleNames = roles.map((r) => r.role.toLowerCase());
        const stored = localStorage.getItem(WORKSPACE_KEY);
        if (stored && roleNames.includes(stored)) {
          setActiveWorkspaceState(stored);
        } else if (roleNames.length > 0) {
          setActiveWorkspace(roleNames[0]);
        } else {
          setActiveWorkspaceState(null);
          localStorage.removeItem(WORKSPACE_KEY);
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load roles");
    } finally {
      setLoading(false);
    }
  }, [wallet, isOwner, setActiveWorkspace]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const apply = useCallback(
    async (role: string) => {
      if (!wallet) throw new Error("Connect a wallet first");
      await applyForRole(wallet, role);
      await refresh();
    },
    [wallet, refresh]
  );

  const approve = useCallback(
    async (id: number, reason?: string) => {
      if (!wallet) throw new Error("Owner wallet required");
      await approveRoleApplication(id, wallet, reason);
      await refresh();
    },
    [wallet, refresh]
  );

  const reject = useCallback(
    async (id: number, reason?: string) => {
      if (!wallet) throw new Error("Owner wallet required");
      await rejectRoleApplication(id, wallet, reason);
      await refresh();
    },
    [wallet, refresh]
  );

  const grant = useCallback(
    async (walletPubkey: string, role: string) => {
      await grantRole(walletPubkey, role);
      await refresh();
    },
    [refresh]
  );

  const revoke = useCallback(
    async (id: number, reason?: string) => {
      if (!wallet) throw new Error("Owner wallet required");
      await revokeRoleApplication(id, wallet, reason);
      await refresh();
    },
    [wallet, refresh]
  );

  const value = useMemo(
    () => ({
      memberships,
      myApplications,
      pendingQueue,
      grantedQueue,
      activeWorkspace,
      setActiveWorkspace,
      loading,
      error,
      refresh,
      apply,
      approve,
      reject,
      revoke,
      grant,
      applyableRoles: APPLYABLE_ROLES,
    }),
    [
      memberships,
      myApplications,
      pendingQueue,
      grantedQueue,
      activeWorkspace,
      setActiveWorkspace,
      loading,
      error,
      refresh,
      apply,
      approve,
      reject,
      revoke,
      grant,
    ]
  );

  return (
    <RolesContext.Provider value={value}>{children}</RolesContext.Provider>
  );
}

/**
 * Access multi-role membership and application queue state.
 */
export function useRoles() {
  return useContext(RolesContext);
}
