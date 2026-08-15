package com.idp.rag;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Folds the platform's French and English vocabulary onto one canonical form.
 *
 * <p>The catalogue's documentation is written in English while the Copilot's UI —
 * and its own suggested questions — are French. A lexical embedding model has no
 * bridge between "paiement" and "payment", so without this step
 * "Comment intégrer l'API de Paiement ?" retrieves whichever document happens to
 * share the most incidental character n-grams. It measurably did: that question
 * ranked the Product Catalog runbook first.
 *
 * <p>Normalising both sides through the same table is what makes the fix sound —
 * ingestion and query embedding call it via {@link HashingEmbeddingModel}, so a term
 * can never be canonical in the index and raw in the query.
 *
 * <p><strong>This is a bridge, not the destination.</strong> It covers the platform's
 * own domain nouns and nothing else; a question phrased entirely in untabulated
 * French still will not match. The real fix is a multilingual embedding model behind
 * {@link EmbeddingModel}, at which point this class can be deleted outright.
 */
final class DomainLexicon {

    private DomainLexicon() {
    }

    /**
     * French (and common variant) surface forms mapped to the English term the
     * documentation uses. Accents are stripped before lookup, so keys are unaccented.
     */
    private static final Map<String, String> CANONICAL = Map.ofEntries(
            // Payments
            Map.entry("paiement", "payment"),
            Map.entry("paiements", "payment"),
            Map.entry("payments", "payment"),
            Map.entry("remboursement", "refund"),
            Map.entry("remboursements", "refund"),
            Map.entry("refunds", "refund"),
            Map.entry("carte", "card"),
            Map.entry("cartes", "card"),
            Map.entry("cards", "card"),
            Map.entry("facturation", "billing"),
            Map.entry("devise", "currency"),
            Map.entry("transaction", "transaction"),
            Map.entry("transactions", "transaction"),

            // Catalogue
            Map.entry("catalogue", "catalog"),
            Map.entry("produit", "product"),
            Map.entry("produits", "product"),
            Map.entry("products", "product"),
            Map.entry("recherche", "search"),
            Map.entry("prix", "pricing"),
            Map.entry("tarification", "pricing"),

            // Notifications
            Map.entry("notification", "notification"),
            Map.entry("notifications", "notification"),
            Map.entry("courriel", "email"),
            Map.entry("alerte", "alert"),
            Map.entry("alertes", "alert"),

            // Scaffolding and delivery
            Map.entry("echafaudage", "scaffolding"),
            Map.entry("gabarit", "template"),
            Map.entry("gabarits", "template"),
            Map.entry("modele", "template"),
            Map.entry("modeles", "template"),
            Map.entry("templates", "template"),
            Map.entry("deploiement", "deployment"),
            Map.entry("deploiements", "deployment"),
            Map.entry("deployments", "deployment"),

            // Authorization
            Map.entry("autorisation", "authorization"),
            Map.entry("authentification", "authentication"),
            Map.entry("role", "role"),
            Map.entry("roles", "role"),
            Map.entry("regle", "rule"),
            Map.entry("regles", "rule"),
            Map.entry("rules", "rule"),
            Map.entry("politique", "policy"),
            Map.entry("politiques", "policy"),
            Map.entry("policies", "policy"),
            Map.entry("droit", "permission"),
            Map.entry("droits", "permission"),
            Map.entry("permissions", "permission"),
            Map.entry("utilisateur", "user"),
            Map.entry("utilisateurs", "user"),
            Map.entry("users", "user"),
            Map.entry("equipe", "team"),
            Map.entry("equipes", "team"),
            Map.entry("teams", "team"),
            Map.entry("securite", "security"),
            Map.entry("suppression", "deletion"),

            // Feature flags
            Map.entry("fonctionnalite", "feature"),
            Map.entry("fonctionnalites", "feature"),
            Map.entry("drapeau", "flag"),
            Map.entry("drapeaux", "flag"),
            Map.entry("flags", "flag"),
            Map.entry("deploiementprogressif", "rollout"),

            // Platform nouns
            Map.entry("services", "service"),
            Map.entry("integrer", "integration"),
            Map.entry("integration", "integration"),
            Map.entry("endpoints", "endpoint"),
            Map.entry("contrat", "contract"),
            Map.entry("contrats", "contract"),
            Map.entry("documentation", "documentation"),
            Map.entry("charges", "charge")
    );

    /**
     * @return the canonical term for {@code token}, or the accent-stripped token when
     *         it is not platform vocabulary
     */
    static String canonical(String token) {
        String folded = stripAccents(token);
        return CANONICAL.getOrDefault(folded, folded);
    }

    /**
     * Accents are stripped for every token, not only tabulated ones, so "déploiement"
     * and "deploiement" share character n-grams instead of looking unrelated.
     */
    private static String stripAccents(String token) {
        String normalized = Normalizer.normalize(token.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
