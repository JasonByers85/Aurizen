package com.aurizen.core

class ModelLoadFailException : Exception("Failed to load model, please check if model file exists and is valid")

class ModelSessionCreateFailException : Exception("Failed to create model session, please try again")