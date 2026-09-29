import { Navigate } from "react-router-dom";

/**
 * Claims live on the stage claim screen (wallet-signed).
 */
export function PaymentClaimPage() {
  return <Navigate to="/stage-map/claim" replace />;
}
