/*
Author: Muntasir Muzahid Chowdhury
COMP 521: Modern Computer Games
Assignment 2
*/
//Note:
//Adapted basic initialization functions related to SDL from LazyFOO
//Specifically the init() and close() functions which are called at
//program startup and shutdown, respectively.

#include <set>
#include <map>
#include <list>
#include <cmath>
#include <ctime>
#include <deque>
#include <queue>
#include <stack>
#include <cctype>
#include <cstdio>
#include <string>
#include <vector>
#include <cassert>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include <iostream>
#include <algorithm>
#include <SDL.h>
#include <stdio.h>
#include <SDL2_gfxPrimitives.h>
#include <unistd.h>

using namespace std;

//Screen dimension constants
const int SCREEN_WIDTH = 640;
const int SCREEN_HEIGHT = 480;

//Mathematical Constants
const double PI = 3.14159265;
const double EPS = 1e-11;

bool init();//Starts up SDL and creates window
void close();//Loads individual image as texture
SDL_Window* gWindow = NULL;//The window we'll be rendering to
SDL_Renderer* gRenderer = NULL;//The window renderer

bool init(){
	//Initialization flag
	bool success = true;

	//Initialize SDL
	if( SDL_Init( SDL_INIT_VIDEO ) < 0 ){
		printf( "SDL could not initialize! SDL Error: %s\n", SDL_GetError() );
		success = false;
	}
	else{
		//Set texture filtering to linear
		if( !SDL_SetHint( SDL_HINT_RENDER_SCALE_QUALITY, "1" ) )
			printf( "Warning: Linear texture filtering not enabled!" );
		//Create window
		gWindow = SDL_CreateWindow( "Q1", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, SCREEN_WIDTH, SCREEN_HEIGHT, SDL_WINDOW_SHOWN );
		if( gWindow == NULL ){
			printf( "Window could not be created! SDL Error: %s\n", SDL_GetError() );
			success = false;
		}
		else{
			//Create renderer for window
			gRenderer = SDL_CreateRenderer( gWindow, -1, SDL_RENDERER_ACCELERATED );
			if( gRenderer == NULL ){
				printf( "Renderer could not be created! SDL Error: %s\n", SDL_GetError() );
				success = false;
			}
			else
				SDL_SetRenderDrawColor( gRenderer, 0xFF, 0xFF, 0xFF, 0xFF );
		}
	}

	return success;
}

void close(){
	//Destroy window
	SDL_DestroyRenderer( gRenderer );
	SDL_DestroyWindow( gWindow );
	gWindow = NULL;
	gRenderer = NULL;
	//Quit SDL
    SDL_Quit();
}

//Structure for points on mountain
struct mtPoints{
    int id;//This is the initial non-deformed x value, and is used to maintain order of polygon. Also the key for set's sorting function.
    mutable int x, y;
    mtPoints( int a, int b ){
        id = a;
        x = a, y = b;
    }
};

bool operator < (const mtPoints a, const mtPoints b){
    return a.id < b.id;
}

//Contains the set of points representing each mountain
set< mtPoints > mntn1, mntn2;

bool operator < ( const pair<int, int> a, const pair<int, int> b ){
    return a.first < b.first;
}

const int midptIterationBound = 3;//4//Frequency of bisections
const int defVar = 30;//40//Default starting variation in height range for points in the midpt. bisection method
const int decreaseVar = 5;//5//Value by which the variation decreases
const int peakMin = 150, peakMax = 160;//50//60//Range for first midpt. in bisection
const int mountainWidth = 200, mountainBaseY = 400;//Width of mountain, and base coordinate
int mountainBMidPoint;//X value of middle of second mountain
int yBound = peakMin;//Maximum y value all second mountain points can have. Used later for vertical smoothing.

pair< int, int > canonPt;//Holds coordinates of the canon
pair<int, int> waterLine[2];//Holds coordinates of the waterbody

//Pixel grid by which collision detection/resolution will be modelled
bool collisionGrid[SCREEN_WIDTH][SCREEN_HEIGHT];

//Calculating points of the mountain using Midpoint Bisection method
//The two points make up the first 4 parameters.
//mn and mx are the minimum and maximum y value the randomly generated point can have
//mntn is the mountain we're working with
//rangeVar is the variation in values used to determine mn and mx for the next function call.
void midptBisection( int ax, int ay, int bx, int by, int mn, int mx, set< mtPoints > *mntn, int rangeVar ){

    //Break condition. Stops if points become too close too each other.
    if( bx - ax < midptIterationBound || mx <= mn ) return;

    //Generate new midpoint
    int midy = mn + (rand() % (mx - mn));//the randomly generated height for this point (falls withing mn and mx)

    mtPoints mid = mtPoints(0,0);
    mid.x = (ax + bx) / 2;
    mid.y = midy;
    mid.id = mid.x;
    mntn->insert(mid);//Insert the midpoint into the list of points for the mountain

    int rng1 = (ay + mid.y) / 2;//Centre of range for first function call i.e. rng1 +/- rangeVar
    int rng2 = (mid.y + by) / 2;//Centre of range for second function call

    //Variation is decreased over time by a certain amount. Changing this amount with defVar and midptIterationBound
    //allows us to achieve the desired contour for the mountain
    rangeVar -= decreaseVar;
    if( rangeVar < 0 ) rangeVar = 0;
    if( rng1 + rangeVar > mountainBaseY ) rng1 = mountainBaseY - rangeVar;//the max value can't be greater than the mountainBase
    if( rng2 + rangeVar > mountainBaseY ) rng2 = mountainBaseY - rangeVar;

    midptBisection( ax, ay, mid.x, mid.y, rng1 - rangeVar, rng1 + rangeVar, mntn, rangeVar );
    midptBisection( mid.x, mid.y, bx, by, rng2 - rangeVar, rng2 + rangeVar, mntn, rangeVar );
}

//Global read-only copies of the arrays ultimately used to construct mountains
Sint16 Gcopy_Mvx[500], Gcopy_Mvy[500], Gcopy_pts;

//Rendering mountains and generating new collision grid, involves smoothing deformities as well
void renderMountains(){
    SDL_SetRenderDrawColor( gRenderer, 0x00, 0xFF, 0x00, 0xFF );
    set< mtPoints >::iterator sit;

    //Used to collect x and y values of mountains coordinates. To be sent to polygon function.
    Sint16 Mvx[500], Mvy[500], pts;

    //Collecting Mountain A points
    for( sit = mntn1.begin(); sit != mntn1.end(); sit++ ){
        mtPoints  tmp = *sit;
        Mvx[pts] = tmp.x, Mvy[pts] = tmp.y, ++pts;
    }

    memset( collisionGrid, 0, sizeof(collisionGrid) );
    int fx = 0, fy = 0;//temporary variables
    int pt1, pt2;

    //Collecting Mountain B points
    //Also calculating collision grid and carrying out mountain deformity smoothing
    for( sit = mntn2.begin(); sit != mntn2.end(); sit++ ){
        if( sit == mntn2.begin() ) pt1 = pts + 1;
        else if( sit != mntn2.end() ) pt2 = pts + 1;
        mtPoints tmp = *sit;
        Mvx[pts] = tmp.x, Mvy[pts] = tmp.y, ++pts;


        //Smoothing
        set< mtPoints >::iterator sitNext = sit;
        sitNext++;

        //Y value smoothing of deformed mountain
        if( yBound > tmp.y  ){
                int diff = yBound - sit->y;
                sit->y = yBound + (5 - diff);
        }
        //X value smoothing of deformed mountain
        if( sitNext != mntn2.end() ){
            //Not letting two adjacent points get too far away from each other
            if( sitNext->x - sit->x > midptIterationBound + 1 ){
                sit->x += 1;
                if( sit == mntn2.begin() && yBound < waterLine[0].second - 5 ) yBound += 1;
            }
            else if( sitNext->x < sit->x + 1 )
                sitNext->x += 1;

        }

        //Creating collision grid for second mountain
        if( sit != mntn2.begin() ){
            //Using -2 and +2 in following limits makes the roll seem less
            //accurate but is required to ensure there isn't the occasional bug
            for( int i = min(fx,tmp.x) - 2; i <= max(fx,tmp.x) + 2; i++ )
                 for( int j = max( fy, tmp.y ); j >= min( fy, tmp.y ); j-- )
                        collisionGrid[i][j] = true;
        }
        fx = tmp.x, fy = tmp.y;
    }

    //Refining collision grid by filling in most of the inner blocks
    int a = pt1, b = pt2;
    while( a < b ){
        if( abs( Mvy[a] - Mvy[b] ) < 5 ){
            for( int i = min( Mvx[a], Mvx[b] ); i <= max( Mvx[a], Mvx[b] ); i++ )
                 for( int j = max( Mvy[a], Mvy[b] ); j >= min( Mvy[a], Mvy[b] ); j-- )
                        collisionGrid[i][j] = true;
            a++, b--;
        }
        else if( Mvy[b] > Mvy[a] ) a++;
        else b--;
    }

    //-----Rendering-----//
    //Render water body
    SDL_Rect fillRect = { waterLine[0].first - 10, waterLine[0].second + 5, mountainWidth, mountainBaseY - waterLine[0].second + 1 - 5};
    SDL_SetRenderDrawColor( gRenderer, 51, 51, 255, SDL_ALPHA_OPAQUE );
    SDL_RenderFillRect( gRenderer, &fillRect );

    //Render mountains as a polygon
    filledPolygonRGBA ( gRenderer, Mvx, Mvy, pts, 76, 153, 0x00, SDL_ALPHA_OPAQUE);//Draws the mountains0xFF0000FF
    //polygonRGBA ( gRenderer, Mvx, Mvy, pts, 76, 153, 0x00, SDL_ALPHA_OPAQUE);//Draws the mountains0xFF0000FF

    //Copying coordinates to a global array for easy reading. Same values are also in the "set"
    memcpy( Gcopy_Mvx, Mvx, sizeof(Gcopy_Mvx));
    memcpy( Gcopy_Mvy, Mvy, sizeof(Gcopy_Mvy));
    Gcopy_pts = pts;
}

//Starting coordinates for canon's ballast(which is a rectanlge)
double rectx[4], recty[4];
Sint16 Vx[4], Vy[4];

//Calculates canon's new position according to angle and canon's original position
void calculateCanon( double ang ){
    ang = ang * (PI/180);
    for( int i = 0; i < 4; i++ ){
        Vx[i] = (Sint16)( ((rectx[i] - rectx[0]) * cos(ang)) - ((recty[i] - recty[0]) * sin(ang)) + rectx[0]);
        Vy[i] = (Sint16)( ((rectx[i] - rectx[0]) * sin(ang)) + ((recty[i] - recty[0]) * cos(ang)) + recty[0]);
    }
}

//Generates the points for mountains using midpt. bisection
void generateMountains(){
        //------MountainA-------//
        //Entering the two endpoints of the mountain into the list
        const int mountainA_X1= 100;
        const int mountainA_X2 = mountainA_X1 + mountainWidth;
        mntn1.insert(mtPoints( mountainA_X1, mountainBaseY ));
        mntn1.insert(mtPoints( mountainA_X2, mountainBaseY ));

        //Calling mid-point bisection for mountain A
        midptBisection( mountainA_X1, mountainBaseY, mountainA_X2, mountainBaseY, peakMin, peakMax, &mntn1, defVar );

        set< mtPoints >::iterator sit;//To iterate through points
        int cnt = 0;
        for( sit = mntn1.begin(), cnt = 0; sit != mntn1.end(); sit++, cnt++ ){
            mtPoints tmp = *sit;
            if( cnt == mntn1.size() - 6 )//setting point for waterline
                waterLine[0].first = tmp.x, waterLine[0].second = tmp.y;
            if( cnt == 42 )//setting point for canon
                canonPt.first = tmp.x, canonPt.second = tmp.y;
        }

        //------MountainB-------//
        //Entering the two endpoints of the mountain into the list
        const int mountainB_X1 = mountainA_X2 + 50;
        const int mountainB_X2 = mountainB_X1 + mountainWidth;
        mntn2.insert(mtPoints( mountainB_X1, mountainBaseY ));
        mntn2.insert(mtPoints( mountainB_X2, mountainBaseY ));
        mountainBMidPoint = (mountainB_X1 + mountainB_X2) / 2;

        //Calling mid-point bisection for mountain B
        midptBisection( mountainB_X1, mountainBaseY, mountainB_X2, mountainBaseY, peakMin, peakMax, &mntn2, defVar );

        int fx = 0, fy = 0;
        for( sit = mntn2.begin(); sit != mntn2.end(); sit++ ){
            mtPoints tmp = *sit;
            //Setting other point for waterline
            if( tmp.y <= waterLine[0].second ){
                     waterLine[1].first = tmp.x;
                     waterLine[1].second = tmp.y;
                     break;
            }
        }
}

//Used to assign id to each canonball.
int canonCount;
//Data type representing a canonball
struct ballType{
    int id;//Unique ID for each ball. The key for the set's soring function.
    mutable int x, y;//Initial coordinates
    mutable double ang, Vx, Vy, t;//Angle at which it was shot, initial velocities in x and y direction, and time t
    mutable bool firstCollision, secondCollision;//To signify whether ball has has its first and second collisions
    mutable bool stationary;//whether ball has come to a rest
};

//Set containing currently active canonballs
set<ballType> canonList;

bool operator < ( const ballType a, const ballType b ){
    return a.id < b.id;
}

//Size of the vertical grid
const int perlinSize = 401;
//Arrays to keep the wind strength and direction.
//windOriginal[] has generated noise, wind[] has current noise to be rendered and uses on a cosine function
//with changing value to vary windOriginal values
double wind[perlinSize], windOriginal[perlinSize];

//Draws the vertical bar representing wind strenght and direction
void renderWind(){
    SDL_SetRenderDrawColor( gRenderer, 255, 255, 0x00, 0xFF );
    for( int i = 0; i < perlinSize; i++ ){
        double x;
        x = wind[i] * 10;
        //Printing every 3rd wind vector. All wind vectors will be taken into account during calculation.
        if( i % 3 == 0 ) SDL_RenderDrawLine( gRenderer, 600, i, 600 + (2 * x), i );
    }
}

//Generates the wind strength/direction values and varies them over time using a cosine function
void generateWind( int angle ){
    int i;
    if( angle == -1 ){//For the very first call
        //Generating random noise at intervals of size 10
        //Value between -1 and +1
        for( i = 0; i < perlinSize; i += 10 )
            windOriginal[i] = -1 + ( (double)(rand() % 100)/ 50 );
        //Interpolating the intermediate points
        for( i = 0; i < perlinSize; i++ ){
            if( i % 10 == 0 ) continue;
            double a = windOriginal[i - (i % 10)], b = windOriginal[i - (i % 10) + 10];
            double  t = (double)(i % 10) / 10.0;
            windOriginal[i] = (a * (1.0 - t)) + (b * t);
        }
        memcpy( wind, windOriginal, sizeof(wind) );
    }

   else{
       //After every three increments insert a new set of values into the first 10 places
       //and shift the rest 10 places forward
        if( angle % 15 == 0 ){
            memcpy( wind, windOriginal, sizeof(windOriginal) );
            for( i = 10; i < perlinSize; i++ ) windOriginal[i] = wind[i - 10];
            windOriginal[0] = -1 + ((double)(rand() % 100)/ 50);
            for( i = 1; i < 10; i++ ){
                double a = windOriginal[i - (i % 10)], b = windOriginal[i - (i % 10) + 10];
                double t = (double)(i % 10) / 10.0;
                windOriginal[i] = (a * (1.0 - t)) + (b * t);
            }
        }
        //Varies the values over time using the cosine function
        double angRad = (double)angle * (PI/180.0);
        for( i = 0; i < perlinSize; i++ )
            wind[i] = windOriginal[i] * cos(angRad);
    }
}

//Controls features. Adds features in the order of questions in the assignment.
int featureToggle;//Values - 1, 2, 3, 4, 5

int main( int argc, char* args[] ){
	//Start up SDL and create window
	if( !init() ) printf( "Failed to initialize!\n" );
	else{
	    featureToggle = 5;
        bool quit = false;//Flag for program exit
        SDL_Event e;//Event handler
        //Clear screen
        SDL_SetRenderDrawColor( gRenderer, 0x00, 0x00, 0x00, 0xFF );
        SDL_RenderClear( gRenderer );
        srand(time(0));//seeding the random() function with computer clock

        //Generate points for mountains
        generateMountains();

        //Setting up the canon's ballast (modlled as a rectangle/polygon)
        double angle = 0;//Angle of canonball

        //X and Y coordiantes of for default canon position
        rectx[0] = canonPt.first, recty[0] = canonPt.second;
        rectx[1] = rectx[0], recty[1] = recty[0] - 10;
        rectx[2] = rectx[0] + 20, recty[2] = recty[1];
        rectx[3] = rectx[0] + 20, recty[3] = recty[0];

        //Vx and Vy contains canon's current position
        for( int i = 0; i < 4; i++ ) Vx[i] = (Sint16)rectx[i], Vy[i] = (Sint16)recty[i];

        renderMountains();//Draws the mountains and waterline
        filledPolygonRGBA ( gRenderer, Vx, Vy, 4, 204, 0, 0x00, SDL_ALPHA_OPAQUE);//Draws the canon
        int ballSpeed = 5;//Default firing speed

        canonCount = 0;

        //Angle to be sent to perlin noise function, and the increment by it will be changed over time
        int perlinAngle = 0, diff = -5;

        //Generating initial wind
        generateWind( -1 );//Perlin Noise

        //Time variables ensure canon delay, and removing canonballs that have come to a rest
        clock_t canonStart, ballStart, perlinStart;
        canonStart = clock(), perlinStart = clock();
        const double canonDelay = 0.4;//DECREASE this to increase shooting frequency
        const int maxBallSpeed = 16;//INCREASE this to increase maximum speed at which a ball can be shot

        while( !quit ){

            //To signify whether canon has been moved, and whether a shot has been fired
            bool change = false, shot = false;

            while( SDL_PollEvent( &e ) != 0 ){//Event detector

                if( e.type == SDL_QUIT ) quit = true;//Window exited

                else if( e.type == SDL_KEYDOWN ){//Checking for keyboard input
                    switch( e.key.keysym.sym ){
                        case SDLK_1://Pressed 1
                        featureToggle = 1;
                        break;

                        case SDLK_2://Pressed 2
                        featureToggle = 2;
                        break;

                        case SDLK_3://Pressed 3
                        featureToggle = 3;
                        break;

                        case SDLK_4://Pressed 4
                        featureToggle = 4;
                        break;

                        case SDLK_5://Pressed 5
                        featureToggle = 5;
                        break;

                        case SDLK_UP://Pressed Up
                        if( angle != -90 ) angle -= 10;
                        change = 1;
                        break;

                        case SDLK_DOWN://Pressed Down
                        if( angle < 0 ) angle += 10;
                        change = 1;
                        break;

                        case SDLK_LEFT://Pressed Left
                        if( ballSpeed > 1 ) --ballSpeed;
                        break;

                        case SDLK_RIGHT://Pressed Right
                        if( ballSpeed < maxBallSpeed ) ++ballSpeed;
                        break;

                        case SDLK_SPACE:////Pressed Space
                        //Allowing shot if sufficient time has passed since last shot
                        if( (clock() - canonStart) / (double) CLOCKS_PER_SEC > canonDelay ){
                            //Resetting shot clock
                            canonStart = clock();
                            if( featureToggle > 1 )//Feature Toggle
                                shot = true;
                        }
                        default:
                        break;
                    }
                }
            }

            //Calculating canon's new position if it's moved
            if( change ) calculateCanon( angle );

            //Clearing screen
            SDL_SetRenderDrawColor( gRenderer, 0x00, 0x00, 0x00, 0xFF );
            SDL_RenderClear( gRenderer );//Clearing screen

            //Drawing mountains
            renderMountains();
            //Draws the canon
            filledPolygonRGBA ( gRenderer, Vx, Vy, 4, 204, 0, 0x00, SDL_ALPHA_OPAQUE);
            //Drawing canon velocity meter
            for( int i = 1; i <= ballSpeed; i++ )
                rectangleRGBA( gRenderer, 103 + (i * 3), 20, 100 + (i * 3), 30, 255, 255, 255, 0xFF  );
            //Drawing feature toggle meter
            for( int i = 1; i <= featureToggle; i++ )
                boxRGBA( gRenderer, 98 + (i * 5), 50, 100 + (i * 5), 60, 255, 128, 0, 0xFF  );

            //Perlin Noise update
            if( featureToggle > 2 ){//Feature Toggle
                if( (clock() - perlinStart) / (double) CLOCKS_PER_SEC > 1 ){
                    generateWind( perlinAngle );
                    if( perlinAngle == 70 || perlinAngle == 0 ) diff = -diff; //0 to 60
                    perlinAngle += diff;
                    perlinStart = clock();
                }
                //Drawing wind strength/direction chart
                renderWind();
            }

            //Creates new canonball if shot fired
            if( shot ){
                ballType tmp;
                tmp.id = canonCount++;
                tmp.x = Vx[2] + 5, tmp.y = (Vy[2] + Vy[3]) / 2, tmp.Vx = ballSpeed, tmp.Vy = ballSpeed, tmp.ang = angle * (PI / 180);
                tmp.stationary = false;
                tmp.firstCollision = true;
                tmp.t = 0;
                //Inserting into list of currently present canonballs
                canonList.insert( tmp );
            }

            //CANONBALL motion
            set< ballType >::iterator bt_it;
            const double gravity = 0.5;//Gravitational force
            //Iterating through list of current canonballs and
            for( bt_it = canonList.begin(); bt_it != canonList.end(); ){
                ballType tmp = *bt_it;
                //If the canonball is stationary, draw in current position and continue
                if( bt_it->stationary ){
                    filledCircleRGBA( gRenderer, bt_it->x, bt_it->y, 3, 255, 255, 255, SDL_ALPHA_OPAQUE );
                    bt_it++;
                    continue;
                }
                //Increment time
                bt_it->t += 0.3;//0.5
                //Calculate new x and y value using standar projectile motino equations
                int tx = (int)( (double)bt_it->x + ( bt_it->Vx * cos(bt_it->ang) * bt_it->t) );
                int ty = (int)( (double)bt_it->y + (( bt_it->Vy * sin(bt_it->ang) * bt_it->t ) + (0.5 * bt_it->t * bt_it->t * gravity)));

                //COLLISION DETECTION//

                //Flag to signify if deformation has occured
                bool deform = false;

                //Checking for collision with mountain
                if( collisionGrid[tx][ty] == true && featureToggle > 3 ){//featureToggle
                    //Determining type of collision

                    //First collision with mountain
                    if( bt_it->firstCollision ){
                        //Deformation occurs
                        deform = true;
                        bt_it->firstCollision = false;
                        bt_it->secondCollision = true;
                        bt_it->t = 0;
                        //Setting initial x and y to current x and y
                        bt_it->x = tx, bt_it->y = ty;
                        //Stopping ball first by nullifying velocity and angle. It will still be pulled down by gravity.
                        bt_it->Vx = 0, bt_it->Vy = 0;
                        bt_it->ang = 0;
                    }
                    //Second collision
                    else if( bt_it->secondCollision ){
                        //Giving it a little bounce
                        bt_it->Vy = 0.2;
                        bt_it->t = 0;
                        bt_it->x = tx, bt_it->y = ty;
                        //And a little horizontal velocity to roll down
                        bt_it->Vx = -0.05;//-0.2
                        bt_it->secondCollision = false;
                    }
                    //All other collisions
                    else{
                        //Checkin if ball should stop moving
                        bool stuck = false;
                        //Going through the set to see if there is a slope or flat surface ahead
                        for( int i = Gcopy_pts - 1; i >= (Gcopy_pts/2) + 1; i-- ){
                            if( Gcopy_Mvx[i] == tx ){
                                if( Gcopy_Mvy[i - 1] <= Gcopy_Mvy[i] ){
                                    stuck = true;
                                    bt_it->stationary = true;
                                    bt_it->x = tx, bt_it->y = ty;
                                    ballStart = clock();
                                }
                                break;
                            }
                        }
                        //Decreasing the bounce amount if ball is not stuck
                        if( !stuck ){
                            bt_it->t = 0;
                            bt_it->x = tx, bt_it->y = ty;
                            if( bt_it->Vy > 0.1 ) bt_it->Vy -= 0.05;
                            else bt_it->Vy = 0;
                        }
                    }
                }
                //If no collsion APPLY WIND EFFECTS
                else{
                     //Normal Air Resistance
                     if( bt_it->Vx > 0.02 ) bt_it->Vx -= 0.01;
                     //WIND Interaction (Happens as long as ball has not collided at all with the mountain)
                     if( bt_it->firstCollision == true && featureToggle > 2 )//Feature Toggle
                        bt_it->Vx += (wind[ty] / 5.0);
                }

                //Removing ball from set if it goes out of the screen
                if( tx > SCREEN_WIDTH || ty > mountainBaseY || tx <= 0 || ty <= 0 )
                    canonList.erase(bt_it++);
                //Otherwise draw the ball
                else{
                    filledCircleRGBA( gRenderer, tx, ty, 3, 255, 255, 255, SDL_ALPHA_OPAQUE );
                    bt_it++;
                }

                //Simulating deformation
                if( deform && featureToggle > 4 ){
                    set< mtPoints >::iterator hit;
                    for( hit = mntn2.begin(); hit != mntn2.end(); hit++ ){
                        //Shifting point towards the right, and changing y bound for smoothing
                         if( hit->x >= tx && hit->y <= ty ){
                            if( hit->x + 1 < mountainBMidPoint - 5 )
                                hit->x += 1;
                            else if( yBound < waterLine[0].second - 5 )
                                yBound++;
                            break;
                         }
                    }
                }
            }

            //Update screen
            SDL_RenderPresent( gRenderer );

            //Removing all canonballs that have stopped but only if enough time has passed
            //so that it is effect visible on screen.
            if( (clock() - ballStart) / (double) CLOCKS_PER_SEC > 1 ){
                set<ballType>::iterator bt_it;
                for( bt_it = canonList.begin(); bt_it != canonList.end(); ){
                    if( bt_it->stationary )
                        canonList.erase(bt_it++);
                    else
                        bt_it++;
                }
                //Resetting ball clock
                ballStart = clock();
            }
        }
	}

	//Closing SDL
	close();

	return 0;
}





