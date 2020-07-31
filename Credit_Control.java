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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 * CreditControl.java
 *
 * 
 */
public class CreditControl
{
  private String user;                  // identifies the user
  private Date date_off;                // date when a given user is unregistered
  
  public int state;                     // 0 - available, 1 - calling, 2 - in a call (caller), 3 - in a call (callee)
  public String callID;                 // call ID for security
  public Timers timer;                  // call timers

  private float credit;                 // ammount of credit
  public boolean is_registered;         // controls if user is registered
  
  public CreditControl(String user, Date date)
  {
    this.is_registered = true;
    this.credit = 100;
    this.user = user;
    this.state = 0;
    this.date_off = date; //new SimpleDateFormat("dd/MM/yyyy 'at' HH:mm:ss").format(date);
  }
  
  public String getNotification()
  {
    return "Dear " + this.user + ", your credit is " + this.credit + ".";
  }

  @Override
  public int hashCode()
  {
    //return (callee + date).hashCode();
    return (user).hashCode();
  }

  @Override
  public boolean equals( Object obj )
  {
    if(obj != null && obj instanceof CreditControl)
    {
      CreditControl other = (CreditControl)obj;
      //return this.callee.equals(other.callee) && this.date.equals(other.date);
      return this.user.equals(other.user);
    }
    return false;
  }

  public float getCredit()
  {
    return credit;
  }

  public float subCredit(float value)
  {
    credit = credit - value;
    return credit;
  }

  public float refundCredit(float value)
  {
    credit = credit + value;
    return credit;
  }

  public String getUser()
  {
    return this.user;
  }

  // update credit when the user does the DEregister
  public void setDate_off(Date d)
  {
    this.date_off = d;
    this.is_registered = false;
  }

  // update credit when the user does the register
  public void update_register()
  {
    if (!this.is_registered)
    {
      Date now = new Date();
      long diff = now.getTime() - date_off.getTime();
      for (int i = 0, n = i + 1; i < (int)(diff/3600000) + 1; i++)
      {
          this.subCredit(n * ((float)diff/1000/60));
          this.is_registered = true;
          if (this.getCredit() < 0){
              this.credit = 0;
              break;
          }
      }
    }
  }
}    

