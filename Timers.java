/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mobicents.servlet.sip.example;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author root
 */
public class Timers {
    Timer timer;               // timer object
    CreditControl user;        // user's creditcontrol object
    long last_reservation;     // to register the last reservation timestamp

    public Timers(CreditControl user) {
        this.timer = new Timer();
        this.user = user;
        this.last_reservation = 0;
    }

    public void init_call(int seconds){
        Date date = new Date();
        // register the reservation time
        this.last_reservation = date.getTime();
        // if caller (2)
        if (this.user.state == 2)
            this.user.subCredit(20);
        // if callee (3)
        if (this.user.state == 3)
            this.user.subCredit(16);
        // start a 2 min timer that refreshes when ends
        this.timer.schedule(new updateCallTimer(), seconds*1000);
    }

    class updateCallTimer extends TimerTask {
        public void run(){
            Date date = new Date();
            // register new reservation time
            Timers.this.last_reservation = date.getTime();
            // if caller, restart timer, subs credit and warns user if credit will run out
            if (Timers.this.user.state == 2) {
                // reservation for the next 2 minutes
                Timers.this.user.subCredit(20);
                // schedule a new timer object
                Timers.this.timer.schedule(new updateCallTimer(), 120*1000);
                // since the AS is proxy time we can only notify this event
                // and the user must terminate the call by himself
                if (Timers.this.user.getCredit() < 20) {
                    DiameterOpenIMSSipServlet.sendSIPMessage(Timers.this.user.getUser(),
                            "Ficará sem crédito durante a reserva efetuada.");
                }
            }
            // same procedure
            if (Timers.this.user.state == 3) {
                Timers.this.user.subCredit(16);
                Timers.this.timer.schedule(new updateCallTimer(), 120*1000);
                if (Timers.this.user.getCredit() < 16) {
                    DiameterOpenIMSSipServlet.sendSIPMessage(Timers.this.user.getUser(),
                            "Ficará sem crédito durante a reserva efetuada.");
                }
            }
        }
    }

    public void finishCallTimer(){
        Date date = new Date();
        long finish_time = date.getTime();
        // calculate call duration
        long elapsed_time = finish_time - this.last_reservation;
        // refund the non-used reserved time to caller/callee
        if (this.user.state == 2)
            this.user.refundCredit(20 - (((float)elapsed_time/1000)*20)/120);
        if (this.user.state == 3)
            this.user.refundCredit(16 - (((float)elapsed_time/1000)*16)/120);
        // cancel the timer
        this.timer.cancel();
    }
}