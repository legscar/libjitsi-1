/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import ch.imvs.sdes4j.srtp.*;

/**
 * SDES based SRTP MediaStream encryption control.
 *
 * @author Ingo Bauersachs
 */
public interface SDesControl
    extends SrtpControl
{
    /**
     * Name of the config setting that supplies the default enabled cipher
     * suites. Cipher suites are comma-separated.
     */
    public static final String SDES_CIPHER_SUITES =
        "net.java.sip.communicator.service.neomedia.SDES_CIPHER_SUITES";

    /**
     * Set the enabled SDES ciphers.
     *
     * @param ciphers The list of enabled ciphers.
     */
    public void setEnabledCiphers(Iterable<String> ciphers);

    /**
     * Gets all supported cipher suites.
     *
     * @return all supported cipher suites.
     */
    public Iterable<String> getSupportedCryptoSuites();

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    public SrtpCryptoAttribute[] getInitiatorCryptoAttributes();

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and creates the local crypto attribute. Used when the control
     * is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     *
     * @return The local crypto attribute for the answer of the offer or null if
     *         no matching cipher suite could be found.
     */
    public SrtpCryptoAttribute responderSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);

    /**
     * Select the local crypto attribute from the initial offering (@see
     * {@link #getInitiatorCryptoAttributes()}) based on the peer's first
     * matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     *
     * @return A SrtpCryptoAttribute when a matching cipher suite was found.
     * Null otherwise.
     */
    public SrtpCryptoAttribute initiatorSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);

    /**
     * Gets the crypto attribute of the incoming MediaStream.
     * @return the crypto attribute of the incoming MediaStream.
     */
    public SrtpCryptoAttribute getInAttribute();

    /**
     * Gets the crypto attribute of the outgoing MediaStream.
     * @return the crypto attribute of the outgoing MediaStream.
     */
    public SrtpCryptoAttribute getOutAttribute();
}
