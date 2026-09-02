# Frontend / backend integration
The typed API client is in frontend/src/lib/api. Development defaults to http://localhost/api/v1; the container build uses /api/v1. Do not compile a private Docker hostname into browser JavaScript.

Authentication stores the access token in sessionStorage and refresh token in localStorage. A single shared refresh request retries one rejected 401 request; there are no general write retries. A production BFF with HttpOnly/Secure cookies is preferred. Logout clears local tokens, not server-side refresh tokens (revocation is not implemented).

Admin routes require ADMIN in the UI; the gateway enforces ADMIN for catalog writes, inventory adjustments, order listings/status changes and the catalog admin endpoint. Cart/order enforce user ownership inside the services. Customers are explicitly unavailable: no mock records and no credential-bearing user endpoint.

Checkout sends productId, quantity and shipping address. Order service obtains pricing from Product. The idempotency key includes the user's cart/address intent and is retained across retries. Payment is simulated.

Product image paths are normalized through product-images.ts; SafeImage retries with a category-local image then uses an accessible CSS placeholder if both sources fail. Cards retain dimensions. Only HTTPS images.unsplash.com is configured as an external optimized source. Unsupported hosts must fall back locally, not broaden the allowlist.

Guest cart merges are best-effort sequential writes; partial failures need reconciliation rather than blind repeated merging. Wishlist remains browser-local. Search and product pagination are API-backed; admin catalog pagination currently loads the underlying catalog into service memory before slicing and is not intended for millions of records.
