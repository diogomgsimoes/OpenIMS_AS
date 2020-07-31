/*
 * RM - segundo trabalho
 *
 *      Charging in IMS
 *
 *  Rodolfo Oliveira
 *   rado@fct.unl.pt
 *
 */
package org.mobicents.servlet.sip.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.apache.log4j.Logger;

/**
 *
 * This is the SIP Servlet for OpenIMS Integration example.
 * 
 */
public class DiameterOpenIMSSipServlet extends SipServlet {

private static final long serialVersionUID = 1L;

public static Logger logger = Logger.getLogger(DiameterOpenIMSSipServlet.class);

DiameterShClient diameterShClient = null;

private static SipFactory sipFactory;

//Data structure to control the credit of each user
public static HashMap<String, CreditControl> usersCreditDB = new HashMap<String, CreditControl>();

/**
* Default constructor.
*/
public DiameterOpenIMSSipServlet() {}

@Override
public void init(ServletConfig servletConfig) throws ServletException
{
  logger.info("==============================================================================");
  logger.info("==============>                                              =================");
  logger.info("==============>    RM  2019/2020                             =================");
  logger.info("==============>         Trab 2 Charging - FCT                =================");
  logger.info("==============>                                              =================");
  logger.info("==============>   Student:                                   =================");
  logger.info("==============>          Diogo Simoes, n 50236               =================");
  logger.info("==============>                                              =================");
  logger.info("==============================================================================");

super.init(servletConfig);

// Get the SIP Factory
sipFactory = (SipFactory)servletConfig.getServletContext().getAttribute(SIP_FACTORY);

// Initialize Diameter Sh Client
try
{
  // Get our Diameter Sh Client instance
  this.diameterShClient = new DiameterShClient();

  logger.info("==============> RM T2 logger: Diameter OpenIMS SIP Servlet : Sh-Client Initialized successfuly!");
}
catch ( Exception e ) {
  logger.error( "==============> RM T2 logger: Diameter OpenIMS SIP Servlet : Sh-Client Failed to Initialize.", e );
}   
}

    @Override
    protected void doInvite(SipServletRequest request) throws ServletException, IOException
    {
        // get user's SIP Uniform Resource Identifier (URI) - public identity
        String fromURI = request.getFrom().getURI().toString();
        String toURI = request.getTo().getURI().toString();

        // safety variable to facilitate the following logic
        SipServletResponse credit_error = null;

        // online charging with the IMS entities checking with the Online Charging System (OCS)
        // before the session (fetching the users CreditControl data from db)
        CreditControl cControl_caller = DiameterOpenIMSSipServlet.usersCreditDB.get(fromURI);
        CreditControl cControl_callee = DiameterOpenIMSSipServlet.usersCreditDB.get(toURI);

        try
        {
            logger.info("==============> RM T2 logger: Proccessing INVITE (" + request.getFrom() + " -> " + request.getTo() +") Request...");

            // if it is the first request between entities
            // (to be taken care according to the application config)
            if(request.isInitial())
            {
                // get request proxy (creates one if it does not exist)
                Proxy proxy = request.getProxy();
                // if the sip session is starting with this request
                if(request.getSession().getAttribute("firstInvite") == null)
                {                 
                    // if the callee has a profile in the network
                    if (cControl_callee != null) {
                        // create response for no credit error (402)
                        if ((cControl_caller.getCredit() < 50) || (cControl_callee.getCredit() < 16))
                        {
                            credit_error = request.createResponse(402);
                            this.doErrorResponse(credit_error);
                        }

                        // if no credit failure
                        if (credit_error == null) {
                            // credit subtraction (fixed fee)
                            cControl_caller.subCredit(30);
                            // notify the caller of credit subtraction
                            sendSIPMessage(fromURI, cControl_caller.getNotification());
                            // calling state
                            cControl_caller.state = 1;
                        }

                        // create user not found error - if it is de-registered at the moment (404)
                        if (!cControl_callee.is_registered)
                        {
                            SipServletResponse no_user_error = request.createResponse(404);
                            this.doErrorResponse(no_user_error);
                        }

                        // if no errors and states are as expected 
                        else if ((cControl_caller.state == 1) && (cControl_callee.state == 0))
                        {
                            // call id assignment (for later on security verifications)
                            cControl_caller.callID = request.getCallId();
                            cControl_callee.callID = request.getCallId();

                            // add the proxy to the via SIP field to receive signaling packets
                            // contains info about the users
                            request.getSession().setAttribute("firstInvite", true);
                            proxy.setRecordRoute(true);
                            proxy.setSupervised(true);
                            proxy.proxyTo(request.getRequestURI());
                        }
                    }
                    // if the specified callee isn't yet registered in the network
                    else {
                        // credit subtraction (fixed fee)
                        cControl_caller.subCredit(30);
                        // notify the caller of credit subtraction
                        sendSIPMessage(fromURI, cControl_caller.getNotification());
                        // calling state
                        cControl_caller.state = 1;
                        // 404 response
                        SipServletResponse no_user_error = request.createResponse(404);
                        this.doErrorResponse(no_user_error);
                    }
                }
                else
                {
                    proxy.proxyTo(request.getRequestURI());
                }
            }
        } catch (Exception e) {
            logger.error( "==============> RM T2 logger: Failure in doInvite method.", e );
        }
    }

    @Override
    protected void doAck(SipServletRequest request) throws ServletException, IOException
    {
        try
        {
            logger.info("==============> RM T2 logger: Proccessing ACK (" + request.getFrom() + " -> " + request.getTo() +") Request...");
            
            // get URIs
            String fromURI = request.getFrom().getURI().toString();
            String toURI = request.getTo().getURI().toString();

            // get CreditControl identities
            CreditControl cControl_caller = DiameterOpenIMSSipServlet.usersCreditDB.get(fromURI);
            CreditControl cControl_callee = DiameterOpenIMSSipServlet.usersCreditDB.get(toURI);

            // if the caller is calling and callee is available
            if ((cControl_caller.state == 1) && (cControl_callee.state == 0)) {

                // get session id
                String id = request.getCallId();
            
                // if users callIDs match the sessionID
                if (cControl_caller.callID.equals(id) && cControl_callee.callID.equals(id)) {

                    // change parts states to 2 - in a call (caller), 3 - in a call (callee)
                    cControl_caller.state = 2;
                    cControl_callee.state = 3;

                    // instantiate the call timers
                    cControl_caller.timer = new Timers(cControl_caller);
                    cControl_callee.timer = new Timers(cControl_callee);

                    // start the call timers
                    cControl_caller.timer.init_call(120);
                    cControl_callee.timer.init_call(120);

                    // Notify the spending for the 2 min reservation
                    sendSIPMessage(fromURI, cControl_caller.getNotification());
                    sendSIPMessage(toURI, cControl_callee.getNotification());

                    logger.info("==============> Call active.");
                }
            }
            else {
                logger.info("==============> ID incorreto, sem mais processamento.");
            }
        } catch (Exception e) {
            logger.error( "==============> RM T2 logger: Failure in doACK method.", e );
        }
    }

    @Override
    protected void doBye(SipServletRequest request) throws ServletException, IOException
    {
        try
        {
            logger.info("==============> RM T2 logger: Proccessing BYE (" + request.getFrom() + " -> " + request.getTo() +") Request...");

            // get URIs
            String fromURI = request.getFrom().getURI().toString();
            String toURI = request.getTo().getURI().toString();

            // get users credit control profiles
            CreditControl cControl_caller = DiameterOpenIMSSipServlet.usersCreditDB.get(fromURI);
            CreditControl cControl_callee = DiameterOpenIMSSipServlet.usersCreditDB.get(toURI);
            
            // get session id
            String id = request.getCallId();

            // verify states
            if ((cControl_caller.state == 2) && (cControl_callee.state == 3)) {

                // verify IDs
                if (cControl_caller.callID.equals(id) && cControl_callee.callID.equals(id)) {

                    // finish timers
                    cControl_caller.timer.finishCallTimer();
                    cControl_callee.timer.finishCallTimer();

                    // notify the intermediates of their remaining credit
                    sendSIPMessage(fromURI, cControl_caller.getNotification());
                    sendSIPMessage(toURI, cControl_callee.getNotification());

                    // change the states back to available
                    cControl_caller.state = 0;
                    cControl_callee.state = 0;

                    logger.info("==============> Call ended.");
                }
                else {
                    logger.info("==============> ID incorreto, sem mais processamento.");
                }
            }
            
        } catch (Exception e) {
            logger.error( "==============> RM T2 logger: Failure in doBye method.", e );
        }
    }

    @Override
    protected void doSuccessResponse(SipServletResponse response) throws ServletException, IOException
    {
        try
        {
          response.getRequest().getMethod().equals("Invite");
          response.getCallId();

          logger.info("==============> RM T2 logger: Proccessing doSuccessResponse  STATUS(" + response.getStatus() + " from " + response.getFrom().getURI().toString() + ")...");
        } catch (Exception e) {
            logger.error( "==============> RM T2 logger: Failure in doSuccessResponse method.", e );
        }
    }

    @Override
    protected void doErrorResponse(SipServletResponse response) throws ServletException, IOException
    {
        try
        {
            logger.info("==============> RM T2 logger: Proccessing Error Response (" + response.getStatus() + ")...");

            // get URIs
            String fromURI = response.getFrom().getURI().toString();
            String toURI = response.getTo().getURI().toString();

            // get origin and destination
            CreditControl caller = DiameterOpenIMSSipServlet.usersCreditDB.get(fromURI);
            CreditControl callee = DiameterOpenIMSSipServlet.usersCreditDB.get(toURI);

            // busy user, timeout or refused call
            if((response.getStatus() == 486) || (response.getStatus() == 408)
                    || (response.getStatus() == 487))
            {
                // get session ID
                String id = response.getCallId();

                // check if users callIDs match
                if (caller.callID.equals(id) && callee.callID.equals(id)) {
                    // give back the credit (error before call starts)
                    caller.refundCredit(30);
                    // return to available state
                    caller.state = 0;
                    callee.state = 0;
                    sendSIPMessage(fromURI, caller.getNotification());
                }
                else {
                    logger.info("==============> ID incorreto, sem mais processamento.");
                }
            }

            // in case the caller has no credit for reservation (before call start)
            else if(response.getStatus() == 402)
            {
                // return to available state
                caller.state = 0;
                sendSIPMessage(fromURI, "Saldo insuficiente para efetuar reserva.");
                sendSIPMessage(fromURI, caller.getNotification());
                sendSIPMessage(toURI, callee.getNotification());
            }

            // in case the callee isnt found (before call start)
            else if(response.getStatus() == 404)
            {
                // give back the credit (error before call starts)
                caller.refundCredit(30);
                // update state to available
                caller.state = 0;
                sendSIPMessage(fromURI, "O destino nao existe.");
                sendSIPMessage(fromURI, caller.getNotification());
            }

            // for any other call error (during), terminate the timers and notify users
            // if not in call, change states and notify user
            else {
                // get session ID
                String id = response.getCallId();
                // if IDs match
                if (caller.callID.equals(id) && callee.callID.equals(id)) {
                    // if caller is in a call
                    if (caller.state == 2) {
                        if (caller.timer != null) {
                            caller.timer.finishCallTimer();
                            caller.state = 0;
                            sendSIPMessage(fromURI, caller.getNotification());
                        }
                    }
                    // if caller is calling
                    else if (caller.state == 1) {
                        // give back the credit (error before call starts)
                        caller.refundCredit(30);
                        caller.state = 0;
                        sendSIPMessage(fromURI, caller.getNotification());
                    }
                    // if it isn't active (calling or in a call) but registered
                    else if (caller.is_registered) {
                        caller.state = 0;
                        sendSIPMessage(fromURI, caller.getNotification());
                    }
                    // safety state
                    else {
                        // unavaible, fatal error
                        caller.state = 4;
                    }

                    // same logic for the callee
                    if (callee.state == 3) {
                        if (callee.timer != null) {
                            callee.timer.finishCallTimer();
                            callee.state = 0;
                            sendSIPMessage(toURI, callee.getNotification());
                        }
                    }
                    else if (callee.is_registered) {
                        callee.state = 0;
                        sendSIPMessage(toURI, callee.getNotification());
                    }
                    else {
                        callee.state = 4;
                    }
                } else {
                    logger.info("==============> ID incorreto, sem mais processamento.");
                }
            }
        } catch (Exception e) {
          logger.error( "==============> RM T2 logger: Failure in doErrorResponse method.", e );
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    // sends the final message to the user - SMS
    //
    //////////////////////////////////////////////////////////////////////////////
    public static void sendSIPMessage(String toAddressString, String message)
    {
        try
        {
            logger.info( "==============> RM T2 logger: Sending SIP Message [" + message + "] to [" + toAddressString + "]" );

            SipApplicationSession appSession = sipFactory.createApplicationSession();
            Address from = sipFactory.createAddress("RM_T2 <sip:rm_t2@open-ims.test>");
            Address to = sipFactory.createAddress(toAddressString);
            SipServletRequest request = sipFactory.createRequest(appSession, "MESSAGE", from, to);
            request.setContent(message, "text/html");

            request.send();
        } catch (Exception e) {
            logger.error( "==============> RM T2 logger: Failure creating/sending SIP Message notification.", e );
        }
    }
}