from fastapi import APIRouter, HTTPException, status

from ..content import catalog

router = APIRouter(prefix="/learn", tags=["learn"])


@router.get("/tracks")
def tracks():
    """Every track with its units and lesson titles. Steps are fetched per unit."""
    return catalog().summary()


@router.get("/units/{unit_id}")
def unit(unit_id: str):
    u = catalog().units.get(unit_id)
    if u is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "No such unit")
    return u
