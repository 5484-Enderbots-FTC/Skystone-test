/*
 * Copyright (c) 2020 OpenFTC Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.firstinspires.ftc.teamcode.ultimate_goal_code;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.State;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvPipeline;

@Autonomous(name = "wobble + park", group = "auto")
public class auto_wobble_park_FSM extends LinearOpMode {
    ElapsedTime runtime = new ElapsedTime();

    //OpenCV stuff
    OpenCvCamera webcam;
    RingStackDeterminationPipeline pipeline;

    //motors
    DcMotor mtrBL = null, mtrBR = null, mtrFL = null, mtrFR = null, mtrIntake = null;

    Servo svoWobble;

    State currentState;

    
    //constants
    private final double ticksPerMm = 1.68240559922;
    private final double ticksPerMmCalibratedOld = 1.518268;
    private final double ticksPerMmCalibrated = 3.6422;

    /*
    calibraition time:
    told it to go 3ft, it went 1.25ft (15in)
    the current ticks per mm is 1.518
    1.25ft -> 381mm
    3ft    -> 914mm
    914/381 = 2.3 ish
    old calibration * 2.3 = 3.6422

     */


    public static class var{
        private static int RingStackIndentified = 0;
    }

    public enum Zone{
        A,
        B,
        C
    }
    Zone targetZone = Zone.A;

    public enum State{
        DETECT_RING_STACK,
        NO_RINGS,
        ONE_RING,
        FOUR_RINGS
    }

    @Override
    public void runOpMode() {
        if(!isStopRequested()) {
            //openCV config
            int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
            webcam = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
            pipeline = new RingStackDeterminationPipeline();
            webcam.setPipeline(pipeline);

            webcam.openCameraDeviceAsync(() ->
                    webcam.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT)
            );
            //motors
            mtrBL = hardwareMap.get(DcMotorEx.class, "mtrBL");
            mtrBL.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrBL.setDirection(DcMotorEx.Direction.REVERSE);

            mtrBR = hardwareMap.get(DcMotorEx.class, "mtrBR");
            mtrBR.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrBR.setDirection(DcMotorEx.Direction.FORWARD);

            mtrFL = hardwareMap.get(DcMotorEx.class, "mtrFL");
            mtrFL.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrFL.setDirection(DcMotorEx.Direction.REVERSE);

            mtrFR = hardwareMap.get(DcMotorEx.class, "mtrFR");
            mtrFR.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrFR.setDirection(DcMotorEx.Direction.FORWARD);

            mtrIntake = hardwareMap.get(DcMotorEx.class, "mtrIntake");
            mtrIntake.setZeroPowerBehavior(DcMotorEx.ZeroPowerBehavior.BRAKE);
            mtrIntake.setDirection(DcMotorEx.Direction.FORWARD);

            svoWobble = hardwareMap.get(Servo.class, "svoWobble");
            svoWobble.setDirection(Servo.Direction.FORWARD);

            svoWobble.setPosition(0.95);

            telemetry.addData("Status", "Initialized");
            telemetry.addData("Status", "Run Time: " + runtime.toString());
            telemetry.addData("Analysis", pipeline.getAnalysis());
            telemetry.addData("Position", pipeline.position);
            telemetry.update();


            waitForStart();

            currentState = State.DETECT_RING_STACK;

            while (opModeIsActive()) {

                if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.NONE)) {
                    telemetry.addLine("Zone A, no rings");
                    telemetry.update();
                } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.ONE)) {
                    telemetry.addLine("Zone B, one ring");
                    telemetry.update();
                } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.FOUR)) {
                    telemetry.addLine("Zone C, four rings");
                    telemetry.update();
                }

                switch (currentState) {

                    case DETECT_RING_STACK:
                        if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.NONE) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.A;
                            currentState = State.NO_RINGS;
                        } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.ONE) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.B;
                            currentState = State.ONE_RING;
                        } else if ((pipeline.position == RingStackDeterminationPipeline.RingPosition.FOUR) && (var.RingStackIndentified == 1)) {
                            targetZone = Zone.C;
                            currentState = State.FOUR_RINGS;
                        }
                        break;
                    case NO_RINGS:
                        //strafe to right wall
                        encoderStrafe(0.8, 630);
                        //to zone a
                        encoderForward(0.6, 930);
                        //spit
                        svoWobble.setPosition(0.3);

                        //strafe left a bit
                        encoderStrafe(-0.5, -400); //457mm per 1.5ft
                        //park
                        encoderForward(0.8, 150); //152 per 0.5ftc
                        break;
                    case ONE_RING:
                        //to zone b
                        encoderForward(0.8, 1250); //2438mm per 8ft
                        //strafe to right a bit
                        encoderStrafe(0.4, 260); //152 per 0.5ftc
                        //spit
                        svoWobble.setPosition(0.3);
                        //back up park
                        encoderForward(-0.8, -220); //152 per 0.5ftc
                        break;
                    case FOUR_RINGS:
                        //strafe to right wall
                        encoderStrafe(0.8, 630); //914mm per 3ft
                        //to zone a
                        encoderForward(0.8, 1570); //3048mm per 10ft
                        //spit
                        svoWobble.setPosition(0.3);
                        //back up park
                        encoderForward(-0.8, -420); //457mm per 1.5ftc
                        break;
                }

                telemetry.addData("Analysis", pipeline.getAnalysis());
                telemetry.addData("Position", pipeline.position);
                telemetry.update();
            }
        }
    }


    public static class RingStackDeterminationPipeline extends OpenCvPipeline
    {

        private double RingStackIdentified;

        public enum RingPosition
        {
            FOUR,
            ONE,
            NONE
        }

        static final Scalar BLUE = new Scalar(0, 0, 255, 255);
        static final Scalar GREEN = new Scalar(0, 255, 0, 255);

        static final Point REGION1_TOPLEFT_ANCHOR_POINT = new Point(181,98);

        static final int REGION_WIDTH = 25;
        static final int REGION_HEIGHT = 35;

        final int FOUR_RING_THRESHOLD = 150;
        final int ONE_RING_THRESHOLD = 135;

        Point region1_pointA = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x,
                REGION1_TOPLEFT_ANCHOR_POINT.y);
        Point region1_pointB = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x + REGION_WIDTH,
                REGION1_TOPLEFT_ANCHOR_POINT.y + REGION_HEIGHT);

        Mat region1_Cb;
        Mat YCrCb = new Mat();
        Mat Cb = new Mat();
        int avg1;

        // Volatile since accessed by OpMode thread w/o synchronization
        private volatile RingPosition position = RingPosition.FOUR;

        void inputToCb(Mat input)
        {
            Imgproc.cvtColor(input, YCrCb, Imgproc.COLOR_RGB2YCrCb);
            Core.extractChannel(YCrCb, Cb, 1);
        }

        @Override
        public void init(Mat firstFrame)
        {
            inputToCb(firstFrame);

            region1_Cb = Cb.submat(new Rect(region1_pointA, region1_pointB));
        }

        @Override
        public Mat processFrame(Mat input)
        {
            inputToCb(input);

            avg1 = (int) Core.mean(region1_Cb).val[0];

            Imgproc.rectangle(
                    input,
                    region1_pointA,
                    region1_pointB,
                    BLUE,
                    2);

            position = RingPosition.FOUR;
            if(avg1 > FOUR_RING_THRESHOLD){
                var.RingStackIndentified = 1;
                position = RingPosition.FOUR;
            }else if (avg1 > ONE_RING_THRESHOLD){
                var.RingStackIndentified = 1;
                position = RingPosition.ONE;
            }else{
                var.RingStackIndentified = 1;
                position = RingPosition.NONE;
            }

            Imgproc.rectangle(
                    input,
                    region1_pointA,
                    region1_pointB,
                    GREEN,
                    -1);

            return input;
        }

        public int getAnalysis()
        {
            return avg1;
        }
    }
    private void waitFor(double waittime) {
        runtime.reset();
        while (runtime.time() < waittime) {
        }
    }
    private void resetEncoders() {
        mtrFR.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrFL.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrBR.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        mtrBL.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
    }

    private void runToPosition() {
        mtrFR.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrFL.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrBR.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        mtrBL.setMode(DcMotor.RunMode.RUN_TO_POSITION);
    }

    private void brakeMotors() {
        mtrFL.setPower(0);
        mtrFR.setPower(0);
        mtrBL.setPower(0);
        mtrBR.setPower(0);
    }
    private void mtrFRisBusy() {
        while (mtrFR.isBusy()){
        }
    }
    private void mtrBLisBusy() {
        while (mtrBL.isBusy()){
        }
    }
    private void runWithoutEncoder() {
        mtrFR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrFL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrBR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        mtrBL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
    }


    private void forward(double power) {
        mtrBL.setPower(power);
        mtrBR.setPower(power);
        mtrFL.setPower(power);
        mtrFR.setPower(power);
        
        
    }
    private void forwardPosition(int distance_mm) {
        mtrBL.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
        mtrBR.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
        mtrFL.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
        mtrFR.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
    }
    private void encoderForward(double power, int distance_mm){
        resetEncoders();
        forwardPosition(-distance_mm);
        runToPosition();
        forward(power);
        mtrFRisBusy();
        brakeMotors();
        runWithoutEncoder();
    }

    private void strafe(double power) {
        mtrBL.setPower(power);
        mtrBR.setPower(-power);
        mtrFL.setPower(-power);
        mtrFR.setPower(power);
    }
    private void strafePosition(int distance_mm){
        mtrBL.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
        mtrBR.setTargetPosition(-distance_mm*(int)ticksPerMmCalibrated);
        mtrFL.setTargetPosition(-distance_mm*(int)ticksPerMmCalibrated);
        mtrFR.setTargetPosition(distance_mm*(int)ticksPerMmCalibrated);
    }
    private void encoderStrafe(double power, int distance_mm){
        resetEncoders();
        strafePosition(distance_mm);
        runToPosition();
        strafe(power);
        mtrFRisBusy();
        brakeMotors();
        runWithoutEncoder();
    }


}
