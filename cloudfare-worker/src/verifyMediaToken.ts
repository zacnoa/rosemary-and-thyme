import { env } from "cloudflare:workers";
import { importSPKI, JWTPayload, jwtVerify } from "jose";

const public_key = await importSPKI(env.JWT_PUBLIC_KEY, "");

export async function verifyMediaToken(jwt: string): Promise<JWTPayload> {
  const requiredClaims = ["exp", "userId", "recipeId"];

  const payload = await jwtVerify(jwt, public_key, {
    algorithms: ["RS256"],
    issuer: "rosemary_and_thyme",
    audience: "rosemary_and_thyme_worker",
    requiredClaims: requiredClaims,
  });
  return payload.payload;
}
