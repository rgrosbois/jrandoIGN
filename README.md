# jrandoIGN

Application Java/Swing pour afficher des cartes IGN et des traces GPS aux format kml.

## Formule du calcul de la distance

Pour 2 géolocalisations ($\lambda_a, \varphi_a, h_a$)  et ($\lambda_b, \varphi_b, h_b$) **très proches** 

> $\lambda$: latitude, $\varphi$: longitude, $h$: distance au centre de la Terre.

- Les coordonnées cartésiennes correspondantes sont:

$$
\begin{cases}
x = h\cos(\lambda)\cos(\varphi)\\
y = h\cos(\lambda)\sin(\varphi)\\
z = h\sin(\lambda)
\end{cases}
$$

- Les positions étant très proches, on assimile leur distance à la ligne droite entre eux. On utilise alors la formule de Pythagore: 
  
  $$
  \begin{aligned}
d^2 &= (x_a-x_b)^2 + (y_a-y_b)^2 + (z_a-z_b)^2\\
&= x_a^2 + y_a^2 + z_a^2 + x_b^2 + y_b^2 + z_b^2 - 2(x_ax_b+y_ay_b+z_az_b)
\end{aligned}
  $$

En remplaçant avec les expressions ci-dessus, on trouve:

$$
\begin{aligned}
x_a^2+y_a^2+z_a^2 &= h_a^2(\cos^2\lambda_a\cos^2\varphi_a \cos^2\lambda_a\sin^2\varphi_a + \sin^2\lambda_a)\\
  &= h_a^2(\cos^2\lambda_a(\cos^2\varphi_a +\sin^2\varphi_a) + \sin^2\lambda_a)\\
  &= h_a^2(\cos^2\lambda_a + \sin^2\lambda_a)\\
  &= h_a^2
\end{aligned}
$$

On a, de même, $x_b^2 + y_b^2 + z_b^2 = h_b^2$.

Et enfin:

$$
\begin{aligned}
x_ax_b+y_ay_b+z_az_b &= h_ah_b(\cos\lambda_a\cos\varphi_a\cos\lambda_b\cos\varphi_b + \cos\lambda_a\sin\varphi_a\cos\lambda_b\sin\varphi_b + \sin\lambda_a\sin\lambda_b)\\
&= h_ah_b(\cos\lambda_a\cos\lambda_b(\cos\varphi_a\cos\varphi_b+\sin\varphi_a\sin\varphi_b) + \sin\lambda_a\sin\lambda_b)\\
&= 
h_ah_b(\cos\lambda_a\cos\lambda_b\cos(\varphi_a-\varphi_b) + \sin\lambda_a\sin\lambda_b)\\
\end{aligned}
$$

Et donc:

$$
d^2 = h_a^2+h_b^2-2h_ah_b(\cos\lambda_a\cos\lambda_b\cos(\varphi_a-\varphi_b) + \sin\lambda_a\sin\lambda_b)\\
$$


